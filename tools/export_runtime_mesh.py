"""Export the current runtime through Blender MCP; delegates to the engine-aware exporter."""
from pathlib import Path
import runpy
result=runpy.run_path(str(Path(__file__).with_name("export_engine_runtime.py")))["result"]
