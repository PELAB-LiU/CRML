"""LLM client abstraction for CRML generation experiments.

Three backends (Ollama, OpenAI, Anthropic) sit behind a uniform interface.
AgentBase always works with canonical OpenAI-style message dicts; each backend
translates to its native wire format before the API call and normalises the
response back to LLMResponse + ResponseMetrics.
"""

import json
import time
from abc import ABC, abstractmethod
from contextlib import AsyncExitStack
from dataclasses import dataclass, field

import httpx
import ollama
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from mcp.client.streamable_http import streamablehttp_client


# ── Uniform return types ──────────────────────────────────────────────────────

@dataclass
class ToolCall:
    id: str
    name: str
    arguments: dict


@dataclass
class ResponseMetrics:
    """All fields are optional — providers expose different subsets."""
    input_tokens: int | None = None
    output_tokens: int | None = None
    total_tokens: int | None = None
    duration_s: float | None = None
    tokens_per_s: float | None = None
    cache_read_tokens: int | None = None  # Anthropic prompt-cache reads


@dataclass
class LLMResponse:
    content: str | None
    tool_calls: list[ToolCall] = field(default_factory=list)
    metrics: ResponseMetrics = field(default_factory=ResponseMetrics)


# ── Backend ABC ───────────────────────────────────────────────────────────────

class Backend(ABC):
    """Translate between canonical messages/tools and a specific provider's API."""

    @property
    @abstractmethod
    def model(self) -> str: ...

    @abstractmethod
    async def complete(self, messages: list[dict], tools: list[dict]) -> LLMResponse:
        """Single non-streaming completion. Messages and tools are canonical."""
        ...

    def make_assistant_tool_call_message(self, response: LLMResponse) -> dict:
        """Canonical assistant message carrying tool calls (appended to history)."""
        return {
            "role": "assistant",
            "content": response.content,
            "tool_calls": [
                {
                    "id": tc.id,
                    "type": "function",
                    "function": {"name": tc.name, "arguments": json.dumps(tc.arguments)},
                }
                for tc in response.tool_calls
            ],
        }

    def make_tool_result_message(self, tool_call: ToolCall, content: str) -> dict:
        """Canonical tool-result message (appended after each tool execution)."""
        return {"role": "tool", "tool_call_id": tool_call.id, "content": content}


# ── Ollama backend ────────────────────────────────────────────────────────────

class OllamaBackend(Backend):
    def __init__(self, model: str, client: ollama.AsyncClient):
        self._model = model
        self._client = client

    @property
    def model(self) -> str:
        return self._model

    async def complete(self, messages: list[dict], tools: list[dict]) -> LLMResponse:
        t0 = time.monotonic()
        response = await self._client.chat(
            model=self._model,
            messages=[_to_ollama_msg(m) for m in messages],
            tools=tools,   # Ollama accepts the canonical function-calling format
            options={"temperature": 0.6},
        )
        duration_s = time.monotonic() - t0

        msg = response.message
        tool_calls = []
        for i, tc in enumerate(msg.tool_calls or []):
            args = tc.function.arguments
            if isinstance(args, str):
                args = json.loads(args)
            # Ollama omits tool-call IDs; synthesise one for canonical bookkeeping
            tool_calls.append(ToolCall(
                id=f"call_{tc.function.name}_{i}",
                name=tc.function.name,
                arguments=args,
            ))

        pc, ec, ed = response.prompt_eval_count, response.eval_count, response.eval_duration
        metrics = ResponseMetrics(
            input_tokens=pc,
            output_tokens=ec,
            total_tokens=(pc or 0) + (ec or 0) if (pc is not None or ec is not None) else None,
            duration_s=duration_s,
            tokens_per_s=(ec / (ed / 1e9)) if ec and ed else None,
        )
        return LLMResponse(content=msg.content, tool_calls=tool_calls, metrics=metrics)


def _to_ollama_msg(msg: dict) -> dict:
    """Strip canonical fields Ollama doesn't understand."""
    role = msg["role"]
    if role == "tool":
        return {"role": "tool", "content": msg["content"]}
    if role == "assistant" and msg.get("tool_calls"):
        return {
            "role": "assistant",
            "content": msg.get("content"),
            "tool_calls": [
                {
                    "function": {
                        "name": tc["function"]["name"],
                        "arguments": (
                            json.loads(tc["function"]["arguments"])
                            if isinstance(tc["function"]["arguments"], str)
                            else tc["function"]["arguments"]
                        ),
                    }
                }
                for tc in msg["tool_calls"]
            ],
        }
    return msg


# ── OpenAI backend ────────────────────────────────────────────────────────────

class OpenAIBackend(Backend):
    def __init__(self, model: str, client):  # openai.AsyncOpenAI
        self._model = model
        self._client = client

    @property
    def model(self) -> str:
        return self._model

    async def complete(self, messages: list[dict], tools: list[dict]) -> LLMResponse:
        t0 = time.monotonic()
        kwargs: dict = {"model": self._model, "messages": messages, "temperature": 0.6}
        if tools:
            kwargs["tools"] = tools
        response = await self._client.chat.completions.create(**kwargs)
        duration_s = time.monotonic() - t0

        msg = response.choices[0].message
        tool_calls = []
        for tc in msg.tool_calls or []:
            args = tc.function.arguments
            if isinstance(args, str):
                args = json.loads(args)
            tool_calls.append(ToolCall(id=tc.id, name=tc.function.name, arguments=args))

        u = response.usage
        metrics = ResponseMetrics(
            input_tokens=u.prompt_tokens if u else None,
            output_tokens=u.completion_tokens if u else None,
            total_tokens=u.total_tokens if u else None,
            duration_s=duration_s,
        )
        return LLMResponse(content=msg.content, tool_calls=tool_calls, metrics=metrics)


# ── Anthropic backend ─────────────────────────────────────────────────────────

class AnthropicBackend(Backend):
    def __init__(self, model: str, client):  # anthropic.AsyncAnthropic
        self._model = model
        self._client = client

    @property
    def model(self) -> str:
        return self._model

    async def complete(self, messages: list[dict], tools: list[dict]) -> LLMResponse:
        t0 = time.monotonic()
        kwargs: dict = {
            "model": self._model,
            "max_tokens": 8192,
            "messages": _to_anthropic_messages(messages),
        }
        if tools:
            kwargs["tools"] = _to_anthropic_tools(tools)
        response = await self._client.messages.create(**kwargs)
        duration_s = time.monotonic() - t0

        content_text = None
        tool_calls = []
        for block in response.content:
            if block.type == "text":
                content_text = block.text
            elif block.type == "tool_use":
                tool_calls.append(ToolCall(id=block.id, name=block.name, arguments=block.input))

        u = response.usage
        metrics = ResponseMetrics(
            input_tokens=u.input_tokens if u else None,
            output_tokens=u.output_tokens if u else None,
            total_tokens=(u.input_tokens + u.output_tokens) if u else None,
            duration_s=duration_s,
            cache_read_tokens=getattr(u, "cache_read_input_tokens", None),
        )
        return LLMResponse(content=content_text, tool_calls=tool_calls, metrics=metrics)


def _to_anthropic_messages(messages: list[dict]) -> list[dict]:
    """Translate canonical message list to Anthropic's format.

    Key differences:
    - No 'tool' role: consecutive tool results fold into a single user message
      with tool_result content blocks.
    - Assistant tool calls become tool_use content blocks, not a tool_calls key.
    - System messages are dropped here (pass separately via system= if needed).
    """
    result = []
    i = 0
    while i < len(messages):
        msg = messages[i]
        role = msg["role"]

        if role == "system":
            i += 1

        elif role in ("user", "assistant") and not msg.get("tool_calls"):
            result.append({"role": role, "content": msg.get("content") or ""})
            i += 1

        elif role == "assistant" and msg.get("tool_calls"):
            content = []
            if msg.get("content"):
                content.append({"type": "text", "text": msg["content"]})
            for tc in msg["tool_calls"]:
                args = tc["function"]["arguments"]
                if isinstance(args, str):
                    args = json.loads(args)
                content.append({"type": "tool_use", "id": tc["id"],
                                 "name": tc["function"]["name"], "input": args})
            result.append({"role": "assistant", "content": content})
            i += 1

        elif role == "tool":
            # Collect consecutive tool results → one user message
            tool_results = []
            while i < len(messages) and messages[i]["role"] == "tool":
                m = messages[i]
                tool_results.append({"type": "tool_result",
                                     "tool_use_id": m["tool_call_id"],
                                     "content": m["content"]})
                i += 1
            result.append({"role": "user", "content": tool_results})

        else:
            i += 1

    return result


def _to_anthropic_tools(tools: list[dict]) -> list[dict]:
    return [
        {
            "name": t["function"]["name"],
            "description": t["function"].get("description", ""),
            "input_schema": t["function"]["parameters"],
        }
        for t in tools
    ]


# ── Factory ───────────────────────────────────────────────────────────────────

async def create_backend(
    provider: str,
    model: str,
    *,
    host: str | None = None,
    api_key: str | None = None,
    headers: dict | None = None,
) -> Backend:
    """Create and validate a backend.

    Examples::

        # Ollama (local or proxied)
        backend = await create_backend("ollama", "qwen3:14b",
                                        host="https://...", headers=AUTH)

        # OpenAI (or any OpenAI-compatible endpoint)
        backend = await create_backend("openai", "gpt-4o",
                                        api_key=os.environ["OPENAI_API_KEY"])

        # Anthropic
        backend = await create_backend("anthropic", "claude-sonnet-4-6",
                                        api_key=os.environ["ANTHROPIC_API_KEY"])
    """
    provider = provider.lower()

    if provider == "ollama":
        client = ollama.AsyncClient(host=host, headers=headers)
        async with httpx.AsyncClient(headers=headers) as http:
            r = await http.get(f"{host}/api/tags")
            models = [m["name"] for m in r.json().get("models", [])]
            print(f"Ollama is up. Available models: {models}")
            if not any(model in m for m in models):
                raise ValueError(f"Model '{model}' not found. Run: ollama pull {model}")
            print(f"✓ Model '{model}' is ready.")
        return OllamaBackend(model, client)

    elif provider == "openai":
        from openai import AsyncOpenAI
        client = AsyncOpenAI(api_key=api_key, base_url=host)
        print(f"OpenAI backend ready (model={model})")
        return OpenAIBackend(model, client)

    elif provider == "anthropic":
        from anthropic import AsyncAnthropic
        client = AsyncAnthropic(api_key=api_key)
        print(f"Anthropic backend ready (model={model})")
        return AnthropicBackend(model, client)

    else:
        raise ValueError(f"Unknown provider '{provider}'. Choose: ollama | openai | anthropic")


# ── Agent base ────────────────────────────────────────────────────────────────

class AgentBase:
    def __init__(self, backend: Backend, verbose: bool = True):
        self.backend = backend
        self.verbose = verbose
        self.messages: list[dict] = []
        self._sessions: list[ClientSession] = []
        self._stacks: list[AsyncExitStack] = []
        self._tool_to_session: dict[str, ClientSession] = {}
        self._tools: list[dict] = []       # canonical function-calling format
        self._tool_names: list[str] = []

    async def _add_http_session(self, url: str):
        stack = AsyncExitStack()
        self._stacks.append(stack)
        read, write, _ = await stack.enter_async_context(streamablehttp_client(url))
        session = await stack.enter_async_context(ClientSession(read, write))
        await session.initialize()
        self._sessions.append(session)

    async def _add_stdio_session(self, server_params: StdioServerParameters):
        stack = AsyncExitStack()
        self._stacks.append(stack)
        read, write = await stack.enter_async_context(stdio_client(server_params))
        session = await stack.enter_async_context(ClientSession(read, write))
        await session.initialize()
        self._sessions.append(session)

    async def _init_tools(self):
        for session in self._sessions:
            mcp_tools = await session.list_tools()
            for t in mcp_tools.tools:
                self._tool_names.append(t.name)
                self._tool_to_session[t.name] = session
                self._tools.append({
                    "type": "function",
                    "function": {
                        "name": t.name,
                        "description": t.description,
                        "parameters": t.inputSchema,
                    },
                })

    async def _close_all(self):
        for stack in self._stacks:
            await stack.aclose()

    async def chat(self, user_message: str) -> str:
        self.messages.append({"role": "user", "content": user_message})

        if self.verbose:
            print(f"{'='*100}")
            print(f"User: {user_message}")
            print(f"Tools available: {self._tool_names}\n")

        while True:
            response = await self.backend.complete(self.messages, self._tools)

            if not response.tool_calls:
                self.messages.append({"role": "assistant", "content": response.content})
                if self.verbose:
                    print(f"{'-'*100}")
                    print(f"Assistant: {response.content}")
                return response.content

            self.messages.append(self.backend.make_assistant_tool_call_message(response))

            for tool_call in response.tool_calls:
                if self.verbose:
                    print(f"→ Tool call: '{tool_call.name}' args={tool_call.arguments}")

                result = await self._tool_to_session[tool_call.name].call_tool(
                    tool_call.name, tool_call.arguments
                )
                result_text = str(result.content)

                if self.verbose:
                    preview = result_text[:300] + ("..." if len(result_text) > 300 else "")
                    print(f"← Result: {preview}\n")

                self.messages.append(
                    self.backend.make_tool_result_message(tool_call, result_text)
                )

    def reset(self):
        self.messages = []

    async def __aenter__(self):
        raise NotImplementedError

    async def __aexit__(self, *args):
        raise NotImplementedError


# ── Concrete agents ───────────────────────────────────────────────────────────

class HttpAgent(AgentBase):
    def __init__(self, url: str, backend: Backend, verbose: bool = True):
        super().__init__(backend, verbose)
        self.url = url

    async def __aenter__(self):
        await self._add_http_session(self.url)
        await self._init_tools()
        return self

    async def __aexit__(self, *args):
        await self._close_all()


class StdioAgent(AgentBase):
    def __init__(self, server_params: StdioServerParameters, backend: Backend, verbose: bool = True):
        super().__init__(backend, verbose)
        self.server_params = server_params

    async def __aenter__(self):
        await self._add_stdio_session(self.server_params)
        await self._init_tools()
        return self

    async def __aexit__(self, *args):
        await self._close_all()


class MultiAgent(AgentBase):
    def __init__(
        self,
        transports: list[str | StdioServerParameters],
        backend: Backend,
        verbose: bool = True,
    ):
        super().__init__(backend, verbose)
        self.transports = transports

    async def __aenter__(self):
        for transport in self.transports:
            if isinstance(transport, str):
                await self._add_http_session(transport)
            else:
                await self._add_stdio_session(transport)
        await self._init_tools()
        return self

    async def __aexit__(self, *args):
        await self._close_all()
