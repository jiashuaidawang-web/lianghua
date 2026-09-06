"""Regenerate the Lianghua 20-day dual-prompt plan.

This repository snapshot already contains generated Markdown/Spec files.
The canonical structure is: /learn (learning prompts) + /agent/prompts (engineering evolution prompts).
"""
from pathlib import Path
print("Use the included /learn/prompts and /agent/prompts as the canonical daily prompt set.")
print("Each day must follow: Learn Gate -> Engineering Prompt -> Spec -> Code -> Test -> Regression -> Checklist -> Git.")
