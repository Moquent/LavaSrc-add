# MCDN Source Manager for LavaSrc

This source manager integrates with the CDN API to provide audio tracks for Lavalink.

## Configuration

Add the following to your `application.yml` file:

```yaml
plugins:
  lavasrc:
    sources:
      mcdn: true # Enable MCDN Source
    mcdn:
      apiKey: "your api key" # The API key for the MCDN API
      baseUrl: "https://your-cdn-api.example.com" # The base URL for the MCDN API
      userAgent: "Lavasrc" # Optional: The user agent to use for requests
```

## Usage

### Search by Query

To search for tracks by query, use the following format:

```
mcdnsearch:your search query
```

For example:
```
mcdnsearch:Rick Astley Never Gonna Give You Up
```

This will return a playlist with all matching tracks.

### Search by ISRC

To search for tracks by ISRC, use the following format:

```
mcdnisrc:ISRC_CODE
```

For example:
```
mcdnisrc:GBAYE0601498
```

This will return a single track matching the ISRC.

## API Endpoints

The MCDN source manager uses the following API endpoints:

- `/search?q={query}` - Search for tracks by query
- `/isrc/{isrc}` - Search for tracks by ISRC

## Response Format

The API is expected to return JSON in the following format:

```json
[
  {
    "id": "track_id",
    "title": "Track Title",
    "artist": "Artist Name",
    "duration": 123456, // Duration in milliseconds
    "picture": "https://example.com/image.jpg", // Optional
    "isrc": ["ISRC_CODE"], // Array of ISRC codes
    "versions": [
      {
        "version": "version_name",
        "extension": "mp3",
        "format": "MP3",
        "codec": "mp3",
        "bitrate": 320000,
        "durationMillis": 123456,
        "size": 1234567,
        "path": "path/to/file",
        "url": "https://example.com/track.mp3" // Direct download URL
      }
    ]
  }
]
``` 