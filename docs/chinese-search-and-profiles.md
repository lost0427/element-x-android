# Chinese message search and account profiles

## Searching messages

Open global search, select **Messages**, and enter a keyword. Chinese keywords match inside continuous text: `下午` finds `今天下午开会`.
Whitespace and punctuation separate required keywords, so `下午，开会` finds messages containing both. A continuous phrase such as `下午开会` must occur
without intervening characters. Mixed Chinese and Latin queries ignore case and normalize full-width characters on both the query and the message.
Separate Latin keywords match whole words, including words beside Chinese characters, rather than substrings of unrelated Latin words.
Single Han characters and supplementary Han characters are supported. Pinyin and simplified/traditional conversion are not included.

Queries containing Han characters use a compatibility fallback: the Rust SDK reads the existing local index in pages and Kotlin matches the resolved
message bodies, attachment names/captions and sticker descriptions. No separate plaintext index is created. The SDK's raw result list retains its
positional indices; filtering happens only when exposing results. Matching runs off the UI dispatcher, and changing the query or selecting Rooms
cancels the scan. Search failures offer **Retry**.

This fallback scans the full available index and retains the SDK's accumulated results for the lifetime of the search screen. Large histories can take
longer and use more memory than token searches. An SDK-level CJK tokenizer/index remains the preferred future solution for large datasets.
Non-Chinese queries continue using the SDK's normal token search. Only locally indexed messages can be found; unavailable encryption keys or history
not yet backfilled still limit coverage. A result retains its room/event identity for jumping to the original message.

## Managing account profiles

Multiple accounts are enabled by default. In **Settings**, use **Add another account**, or select an existing account to switch to it.
Open Settings for that account and tap its profile to edit its display name or avatar. Each account retains its own Matrix identity and profile.
The account picker shows the current account and homeserver and observes profile changes, additions and removals without reordering rows by last use.

Profile saves validate the editor's account against the active Matrix client before sending any updates. After a complete or partial save, the client
refreshes its profile through the existing session-scoped persistence path, so other account lists can show the saved name and avatar.
Server restrictions and network availability still apply. Existing explicit multi-account feature preferences override the new default.

## Verification

Run the unit tests for `libraries:matrix:impl`, `libraries:accountselect:impl`, `features:home:impl` and `features:preferences:impl`.
Tests cover substring matching, Unicode normalization, required keywords, room filtering, positional updates, pagination failures, cancellation,
retry, live account changes, stable account ordering, profile refresh after partial saves and rejection of cross-account writes.
Screenshot previews append the new failure and account-marker states to their existing providers; screenshot recording is left to CI.

Before release, verify search against a real SDK index with encrypted history and measure time/memory on a large history. Check two accounts on
different homeservers, edit each profile, restart, switch accounts, and sign out of one account while confirming the other remains usable.
