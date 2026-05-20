import glob
import os
import platform
import subprocess
from pathlib import Path

import jpype


class GradleJvm:
    """Builds a Gradle shadowJar and starts a JPype JVM with the fat JAR on the classpath.

    Usage:
        with GradleJvm("/path/to/gradle/project") as jvm:
            MyClass = jpype.JClass("com.example.MyClass")
            ...

    Or without context manager:
        jvm = GradleJvm("/path/to/gradle/project")
        jvm.build()
        jvm.start()
        # ... use jpype ...
        jvm.shutdown()
    """

    def __init__(
        self,
        project_path: str | os.PathLike,
        task: str = "shadowJar",
        subproject: str | None = None,
        subproject_dir: str | None = None,
        jvm_path: str | None = None,
        extra_classpath: list[str] | None = None,
        jvm_args: list[str] | None = None,
    ):
        self.project_path = Path(project_path).resolve()
        self.task = task
        self.subproject = subproject
        # Physical directory of the subproject, when it differs from the task qualifier.
        # E.g. Gradle task is "experiments:shadowJar" but the dir is "submodules/experiments".
        self.subproject_dir = subproject_dir
        self.jvm_path = jvm_path
        self.extra_classpath = extra_classpath or []
        self.jvm_args = jvm_args or []
        self.jar_path: Path | None = None
        self._started_by_us = False

        if not self.project_path.is_dir():
            raise NotADirectoryError(f"Project path does not exist: {self.project_path}")

    def _gradlew(self) -> Path:
        name = "gradlew.bat" if platform.system() == "Windows" else "gradlew"
        wrapper = self.project_path / name
        if not wrapper.exists():
            raise FileNotFoundError(f"Gradle wrapper not found: {wrapper}")
        return wrapper

    def _gradle_task(self) -> str:
        if self.subproject:
            return f"{self.subproject.lstrip(':')}:{self.task}"
        return self.task

    def _find_fat_jar(self) -> Path:
        """Find the fat JAR produced by shadowJar.

        Searches the project's build/libs directories, preferring JARs that carry
        the shadow classifier (-all.jar) or have a custom archive name set in the
        build script.  Falls back to the newest .jar when no -all variant exists.
        """
        search_roots = [self.project_path]
        lookup = self.subproject_dir or (
            self.subproject.lstrip(":/").replace(":", "/") if self.subproject else None
        )
        if lookup:
            search_roots = [self.project_path / lookup, self.project_path]

        candidates: list[Path] = []
        for root in search_roots:
            candidates.extend(Path(p) for p in glob.glob(str(root / "build" / "libs" / "*.jar")))

        if not candidates:
            raise FileNotFoundError(
                f"No JARs found under {self.project_path}/build/libs. "
                "Did the shadowJar task complete successfully?"
            )

        shadow = [j for j in candidates if j.name.endswith("-all.jar")]
        pool = shadow if shadow else candidates
        return max(pool, key=lambda j: j.stat().st_mtime)

    def build(self) -> Path:
        """Run the Gradle shadowJar task and return the path to the fat JAR."""
        gradlew = self._gradlew()
        task = self._gradle_task()
        cmd = [str(gradlew), task]
        print(f"Running: {' '.join(cmd)}  (cwd={self.project_path})")
        result = subprocess.run(cmd, cwd=self.project_path, capture_output=False)
        if result.returncode != 0:
            raise RuntimeError(f"Gradle build failed (exit code {result.returncode})")
        self.jar_path = self._find_fat_jar()
        print(f"Fat JAR: {self.jar_path}")
        return self.jar_path

    def start(self) -> None:
        """Start the JPype JVM.  A JVM can only be started once per process."""
        if jpype.isJVMStarted():
            print("JVM already running — skipping start.")
            return

        if self.jar_path is None:
            raise RuntimeError("Call build() before start(), or use the context manager.")

        classpath = [str(self.jar_path)] + self.extra_classpath
        kwargs: dict = {"classpath": classpath, "convertStrings": False}
        if self.jvm_path:
            kwargs["jvmpath"] = self.jvm_path
        if self.jvm_args:
            kwargs["args"] = self.jvm_args

        jpype.startJVM(**kwargs)
        self._started_by_us = True
        print("JVM started.")

    def shutdown(self) -> None:
        """Shut down the JVM if we started it."""
        if self._started_by_us and jpype.isJVMStarted():
            jpype.shutdownJVM()
            self._started_by_us = False
            print("JVM shut down.")

    def __enter__(self) -> "GradleJvm":
        self.build()
        self.start()
        return self

    def __exit__(self, *_) -> None:
        self.shutdown()
