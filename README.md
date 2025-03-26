# IJ Commit Tracer

An IntelliJ IDEA plugin that analyzes Git commit history and integrates with YouTrack issue tracking and HiBob employee data.

## Features

- **Commit History Visualization**: Lists all commits in Git repositories within your project
- **YouTrack Integration**: Automatically extracts and links YouTrack ticket references from commit messages
- **HiBob Integration**: Maps commit authors to employee data from HiBob
- **Email Mapping**: Maps personal email addresses to corporate identities
- **Commit Analytics**: Provides statistics about commits by author, referenced tickets, and more
- **Issue Highlighting**: Special tracking for blocker and regression tickets
- **Secure Authentication**: Secure storage of API tokens using IntelliJ's credential system

## Installation

1. Download the plugin from the JetBrains Marketplace
2. Install via IntelliJ IDEA: Settings/Preferences → Plugins → Install from disk
3. Restart IntelliJ IDEA

## Configuration

The plugin uses two configuration files:

1. `.env` file for API credentials
2. `email_mappings.json` for email mappings

### .env Configuration

The `.env` file stores API tokens and URLs:

```
# YouTrack API Configuration
YOUTRACK_API_TOKEN=your_youtrack_token_here
YOUTRACK_API_URL=https://youtrack.example.com/api

# HiBob API Configuration
HIBOB_API_TOKEN=your_hibob_api_token_here
HIBOB_API_URL=https://api.hibob.com/v1
```

#### .env File Location

The `.env` file should be placed in:
1. Your project directory (preferred)
2. User home directory (`~/.commitmapper/.env`) as fallback

### Email Mappings Configuration

Email mappings are stored in a JSON file:

```json
{
    "mappings": {
        "personal@gmail.com": "developer@company.com",
        "old.email@hotmail.com": "jane.smith@company.com"
    }
}
```

#### Email Mappings File Location

The email mappings file (`email_mappings.json`) is stored in:
1. Your project directory (preferred)
2. User home directory (`~/.commitmapper/email_mappings.json`) as fallback

#### Sample Email Mappings

Here's a sample email mappings file:

```json
{
    "mappings": {
        "personal@gmail.com": "developer@company.com",
        "old.email@hotmail.com": "jane.smith@company.com",
        "john.personal@outlook.com": "john.doe@company.com",
        "freelancer1234@gmail.com": "consultant@company.com",
        "legacy.account@yahoo.com": "team.lead@company.com"
    }
}
```

The plugin automatically watches for changes to both files and reloads when changes are detected.


## Usage

### Viewing Commit History

1. Go to **Commit Tracer → List Repository Commits**
2. Filter commits by date range if needed
3. View statistics by author, including commit counts and ticket references

### YouTrack Integration

The plugin automatically:
- Extracts YouTrack ticket IDs from commit messages (e.g., PROJ-1234)
- Fetches ticket details from YouTrack including summary and tags
- Highlights tickets marked as blockers or regressions

### HiBob Integration

The plugin automatically:
- Maps commit author emails to corporate identities
- Retrieves employee information from HiBob
- Shows team, department, and other relevant details

## Development

This plugin is built using:
- Kotlin
- IntelliJ Platform SDK
- Gradle build system

### Programmatic Configuration Access

If you need to access configurations programmatically within the plugin:

```kotlin
// Get the configuration service
val configService = ConfigurationService.getInstance(project)

// Access YouTrack settings from .env file
val youtrackToken = configService.getYouTrackToken()
val youtrackUrl = configService.getYouTrackUrl()

// Access HiBob settings from .env file
val hibobToken = configService.getHiBobToken()
val hibobUrl = configService.getHiBobBaseUrl()

// Use email mappings
val standardEmail = configService.mapEmail("personal@gmail.com")
// Returns "developer@company.com" if mapping exists, otherwise returns original email

// Get all email mappings
val allMappings = configService.getAllEmailMappings()
```

### File Monitoring

The plugin continuously monitors both the `.env` file and the email mappings file for changes. When you modify either file:

1. The plugin automatically detects the changes
2. Reloads the configuration
3. Applies the new settings immediately

This allows you to edit the configuration externally and have the changes take effect without restarting the plugin or IDE.

## License

[Specify the license here]

## Support

[Specify support contact information]