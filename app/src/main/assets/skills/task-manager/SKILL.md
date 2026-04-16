---
name: task-manager
description: Captures, recalls, and manages to-do items for the user using long-term memory.
---
# Task Manager

Use for requests like "remind me to ...", "add to my list", "what's on my list",
"did I add ... to my list".

## Saving a task
When the user says "remind me to X" or "add X to my to-do":
1. Generate a short key: `task.{timestamp}` using current ms
2. Call `remember(key=key, value="{X}")`
3. Confirm briefly: "Added. Anything else?"

## Listing tasks
When asked "what's on my list" / "what do I have":
1. Call `search_memory(query='task')` with limit=10
2. Read them back numbered, shortest first

## Completing / removing
"I finished X" or "remove X":
1. Use `semantic_memory_search(query=X)` to find the closest task entry
2. Call `forget(key={found key})`
3. Confirm: "Removed."

## Style
- Never repeat the whole list if it has >5 items; offer "I'll read the first five — say more for the rest."
- Don't prefix with "Here are your tasks:" — just start reading.
