import json
from contextlib import AsyncExitStack

import httpx
import ollama
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from mcp.client.streamable_http import streamablehttp_client


async def create_client(host: str, model: str | None = None) -> ollama.AsyncClient:
    client = ollama.AsyncClient(host=host)
    async with httpx.AsyncClient() as http:
        r = await http.get(f"{host}/api/tags")
        models = [m["name"] for m in r.json().get("models", [])]
        print(f"Ollama is up. Available models: {models}")
        if model is not None:
            if not any(model in m for m in models):
                raise ValueError(f"Model '{model}' not found. Run: ollama pull {model}")
            print(f"✓ Model '{model}' is ready.")
    return client


class AgentBase:
    def __init__(self, model: str, ollama_client: ollama.AsyncClient, verbose: bool = True):
        self.model = model
        self.client = ollama_client
        self.verbose = verbose
        self.messages: list = []
        self._sessions: list[ClientSession] = []
        self._stacks: list[AsyncExitStack] = []
        self._tool_to_session: dict[str, ClientSession] = {}
        self._ollama_tools: list = []
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
                self._ollama_tools.append({
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
            response = await self.client.chat(
                model=self.model,
                messages=self.messages,
                tools=self._ollama_tools,
                options={"temperature": 0.6},
            )
            msg = response.message

            if not msg.tool_calls:
                self.messages.append({"role": "assistant", "content": msg.content})
                if self.verbose:
                    print(f"{'-'*100}")
                    print(f"Assistant: {msg.content}")
                return msg.content

            self.messages.append(msg)

            for tool_call in msg.tool_calls:
                name = tool_call.function.name
                args = tool_call.function.arguments
                if isinstance(args, str):
                    args = json.loads(args)

                if self.verbose:
                    print(f"→ Tool call: '{name}' args={args}")

                result = await self._tool_to_session[name].call_tool(name, args)
                result_text = str(result.content)

                if self.verbose:
                    preview = result_text[:300] + ("..." if len(result_text) > 300 else "")
                    print(f"← Result: {preview}\n")

                self.messages.append({"role": "tool", "content": result_text})

    def reset(self):
        self.messages = []

    async def __aenter__(self):
        raise NotImplementedError

    async def __aexit__(self, *args):
        raise NotImplementedError


class HttpAgent(AgentBase):
    def __init__(self, url: str, model: str, ollama_client: ollama.AsyncClient, verbose: bool = True):
        super().__init__(model, ollama_client, verbose)
        self.url = url

    async def __aenter__(self):
        await self._add_http_session(self.url)
        await self._init_tools()
        return self

    async def __aexit__(self, *args):
        await self._close_all()


class StdioAgent(AgentBase):
    def __init__(self, server_params: StdioServerParameters, model: str, ollama_client: ollama.AsyncClient, verbose: bool = True):
        super().__init__(model, ollama_client, verbose)
        self.server_params = server_params

    async def __aenter__(self):
        await self._add_stdio_session(self.server_params)
        await self._init_tools()
        return self

    async def __aexit__(self, *args):
        await self._close_all()


class MultiAgent(AgentBase):
    def __init__(self, transports: list[str | StdioServerParameters], model: str, ollama_client: ollama.AsyncClient, verbose: bool = True):
        super().__init__(model, ollama_client, verbose)
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
