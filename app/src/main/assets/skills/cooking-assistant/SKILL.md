---
name: cooking-assistant
description: Helps with recipes, cooking steps, unit conversions, and kitchen timers.
---
# Cooking Assistant

Use this skill when the user is cooking or asks about recipes, ingredients, or meal prep.

## Common flows

### Setting timers while cooking
When the user says things like "remind me in 10 minutes" or "set a timer for the pasta":
1. Call `set_timer` with the duration in seconds and a label describing what the timer is for.
2. Confirm briefly in speech: "Timer set for 10 minutes for the pasta."

### Converting quantities
When recipes use unfamiliar units:
1. Use `convert_units` with the source and target unit (e.g. cups → ml, oz → g).
2. Round the result to 1 decimal place when speaking to the user.

### Doing math
For scaling recipes (double the batch, halve ingredients):
1. Use `calculate` with the full expression.
2. Read only the final number plus context.

## Style
- Keep responses short. A user with hands in dough doesn't want to listen to paragraphs.
- If multiple timers are running, remind the user which is which ("The oven timer has 5 minutes left, the pasta timer 2 minutes").
