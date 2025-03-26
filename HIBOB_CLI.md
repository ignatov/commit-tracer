# HiBob CLI - Command Line Interface

This tool provides a command-line interface for fetching and displaying employee information from the HiBob API.

## Building the CLI

To build the standalone JAR file:

```bash
./gradlew hibobCliJar
```

This will create a JAR file in the `build/libs/` directory.

## Configuration

### Configuration File

The HiBob CLI uses a single JSON configuration file (`.env.json`) with the following format:

```json
{
    "hibobToken": "your-hibob-api-token-here",
    "hibobApiUrl": "https://api.hibob.com/v1",
    "emailMappings": {
        "personal@gmail.com": "developer@company.com",
        "old.email@hotmail.com": "jane.smith@company.com",
        "john.personal@outlook.com": "john.doe@company.com"
    }
}
```

### Default Configuration Locations

The system looks for the config file in the following locations, in order:

1. The path specified via a command-line argument (any `.json` file)
2. `.env.json` in the current directory
3. `~/.commitmapper/.env.json` in the user's home directory

### Email Mapping

Email mappings are stored in the `emailMappings` section of the configuration file.

#### Email Mapping Rules

1. If a mapping exists for an email address, the mapped address is used for HiBob lookups
2. If no mapping exists, the original address is used as-is
3. Email lookups in HiBob are case-insensitive
4. Mappings are applied before any HiBob API calls are made

### Authentication

You can provide your HiBob API token in several ways:

1. Command line argument: `token=YOUR_TOKEN`
2. Environment variable: Set `hibobToken` environment variable
3. Configuration file: Set the `hibobToken` field in the `.env.json` file

## Running the CLI

### Using Gradle

You can run the CLI directly with Gradle:

```bash
# List all employees (up to 10 displayed)
./gradlew runHiBobCli -PcliArgs="token=YOUR_TOKEN"

# Fetch a specific employee by email (with automatic email mapping)
./gradlew runHiBobCli -PcliArgs="personal@gmail.com token=YOUR_TOKEN"

# Use a different API URL
./gradlew runHiBobCli -PcliArgs="https://api.hibob.io/v1 token=YOUR_TOKEN"

# Use a config file for credentials and mappings
./gradlew runHiBobCli -PcliArgs="/path/to/.env.json"
```

### Using JAR File

After building the JAR file, you can run it directly:

```bash
# List all employees
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar token=YOUR_TOKEN

# Fetch a specific employee by email (with automatic email mapping)
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar user@example.com token=YOUR_TOKEN

# Use a config file for credentials and mappings
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar /path/to/.env.json
```

### Command-Line Options

```
java -jar hibob-cli.jar [options]

Options:
  [email@example.com]          Email address to lookup
  [https://api.hibob.com/v1]   HiBob API URL
  [token=TOKEN]                HiBob API token (overrides config file)
  [/path/to/.env.json]         Path to config file
  
  --debug, -d                  Enable debug output
  --lists, -l                  List available named lists
  --list=LIST_NAME             Display specific list (title, department)
  --find=TEXT                  Search for items containing text
  --department=ID              Look up department by ID
  --title=ID                   Look up title by ID
  
  --list-mappings              List all email mappings
  --add-mapping=FROM:TO        Add an email mapping (FROM → TO)
  --remove-mapping=EMAIL       Remove a mapping for the specified email
```

### Email Mapping Management

```bash
# List all current mappings
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar --list-mappings

# Add a mapping from personal@gmail.com to work@company.com
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar --add-mapping=personal@gmail.com:work@company.com

# Remove the mapping for personal@gmail.com
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar --remove-mapping=personal@gmail.com

# Specify a custom config file
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar /path/to/.env.json --list-mappings
```

## Debugging

To enable debug logs, use the `--debug` or `-d` flag:

```bash
# With the JAR file
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar --debug /path/to/.env.json

# With Gradle
./gradlew runHiBobCli -PcliArgs="/path/to/.env.json --debug"
```

## Example Output

```
HiBob CLI - 2025-03-24T12:34:56.789
-----------------------------------------------
Using token from configuration file
Using HiBob API URL: https://api.hibob.com/v1
Loaded 5 email mappings from configuration
Mapping email: personal@gmail.com → developer@company.com

Fetching employee information...
Found employee: developer@company.com

Employee Details:
- Name: John Doe
- Email: developer@company.com
- Team: Engineering
- Title: Senior Software Engineer
- Manager: Jane Smith (jane.smith@company.com)
```

## Integration with Version Control Systems

When using the HiBob CLI with Git or other version control systems, email mappings help correctly identify employees who commit using personal email addresses.

```bash
# Example: Process Git commits with email mapping
git log --format="%H %ae" | while read commit email; do
    java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar $email
done
```