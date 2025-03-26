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

The plugin uses a unified JSON configuration file (`.env.json`) for all settings:
- API credentials (YouTrack, HiBob)
- API URLs
- Email mappings

### Configuration File Format

The `.env.json` file uses the following format:

```json
{
    "youtrackToken": "your-youtrack-token-here",
    "youtrackUrl": "https://youtrack.example.com/api",
    "hibobToken": "your-hibob-api-token-here",
    "hibobApiUrl": "https://api.hibob.com/v1",
    "emailMappings": {
        "personal@gmail.com": "developer@company.com",
        "old.email@hotmail.com": "jane.smith@company.com",
        "john.personal@outlook.com": "john.doe@company.com"
    }
}
```

### Configuration File Location

The `.env.json` file should be placed in:
1. Your project directory (preferred)
2. User home directory (`~/.commitmapper/.env.json`) as fallback

The plugin automatically watches for changes to this file and reloads when changes are detected.

### Email Mapping Configuration

Email mappings are stored in the `emailMappings` section of the `.env.json` file.
These mappings help identify employees who commit using personal email addresses.

#### Email Mapping Rules

1. If a mapping exists for an email address, the mapped address is used for HiBob lookups
2. If no mapping exists, the original address is used as-is
3. Email lookups in HiBob are case-insensitive
4. Mappings are applied before any HiBob API calls are made

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

// Access YouTrack settings
val youtrackToken = configService.getYouTrackToken()
val youtrackUrl = configService.getYouTrackUrl()

// Access HiBob settings
val hibobToken = configService.getHiBobToken()
val hibobUrl = configService.getHiBobBaseUrl()

// Use email mappings
val standardEmail = configService.mapEmail("personal@gmail.com")
// Returns "developer@company.com" if mapping exists, otherwise returns original email

// Get all email mappings
val allMappings = configService.getAllEmailMappings()

// Update a configuration value
configService.updateValue("youtrackToken", "new-token-value")
```

### File Monitoring

The plugin continuously monitors the configuration file for changes. When you modify the file:

1. The plugin automatically detects the changes
2. Reloads the configuration
3. Applies the new settings immediately

This allows you to edit the configuration externally and have the changes take effect without restarting the plugin or IDE.

## License

[Specify the license here]

## Support

[Specify support contact information]