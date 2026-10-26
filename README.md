# Crate Sorter

Sorts your Spotify Liked Songs into new mood and genre playlists. It never changes your existing playlists: it only creates new private ones, and Undo removes only those.

**Open it:** https://devilbike.github.io/crate-sorter/

## Setup

1. At https://developer.spotify.com/dashboard, create an app (the owner account needs Spotify Premium) and tick "Web API".
2. Set the Redirect URI to `https://devilbike.github.io/crate-sorter/` exactly, with the trailing slash.
3. Copy the Client ID. No client secret is needed (the app signs in with PKCE).
4. In the app's User Management, add the email of every Spotify account that will use it (up to 5).
5. Open the link above on your phone in Chrome, paste the Client ID, tap Connect, then use "Add to Home screen".

Everything runs in your browser. Your sign-in token and settings are kept only in that browser's storage.
