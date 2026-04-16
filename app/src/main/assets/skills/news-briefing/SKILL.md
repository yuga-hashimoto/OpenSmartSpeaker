---
name: news-briefing
description: Provides daily news briefings combining headlines from multiple sources with weather.
---
# News Briefing

Use when the user asks "what's going on", "give me my briefing", "news update",
or similar morning-briefing style requests.

## Standard briefing flow
1. Call `get_datetime` (so you can open with "Good morning" / "Good evening" appropriately)
2. Call `get_weather` with no location (uses default)
3. Call `get_news` with source='bbc' and limit=3 for world news
4. If the user has a preferred feed saved in memory (search `memory.news_feed`), use that too
5. Summarize in speech as a coherent 3-5 sentence briefing — NOT a list

## Style rules
- Open with time-of-day greeting ("Good morning.")
- Weather before news (users want context first)
- Read 2-3 headlines max, not all 5
- Skip obvious duplicates across feeds
- End with "That's your briefing." — no question unless user invited follow-up

## Error handling
- If news feed fails, skip to weather only — don't stall
- If weather fails, mention only news
- If both fail, apologize briefly and offer to retry later
