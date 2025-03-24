# HiBob CLI - Command Line Interface

This tool provides a command-line interface for fetching and displaying employee information from the HiBob API.

## Building the CLI

To build the standalone JAR file:

```bash
./gradlew hibobCliJar
```

This will create a JAR file in the `build/libs/` directory.

## Running the CLI

### Using Gradle

You can run the CLI directly with Gradle:

```bash
# List all employees (up to 10 displayed)
./gradlew runHiBobCli -PcliArgs="token=YOUR_TOKEN"

# Fetch a specific employee by email
./gradlew runHiBobCli -PcliArgs="user@example.com token=YOUR_TOKEN"

# Use a different API URL
./gradlew runHiBobCli -PcliArgs="https://api.hibob.io/v1 token=YOUR_TOKEN"

# Use a .env file for credentials
./gradlew runHiBobCli -PcliArgs="/path/to/.env"
```

### Using JAR File

After building the JAR file, you can run it directly:

```bash
# List all employees
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar token=YOUR_TOKEN

# Fetch a specific employee by email
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar user@example.com token=YOUR_TOKEN

# Use a .env file for credentials
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar /path/to/.env
```

## Authentication

You can provide your HiBob API token in several ways:

1. Command line argument: `token=YOUR_TOKEN`
2. Environment variable: Set `HIBOB_API_TOKEN` environment variable
3. .env file: Create a file with `HIBOB_API_TOKEN=YOUR_TOKEN` and provide the path to the file

## Debugging

To enable debug logs for the env file reader, set the `debug.env` system property to `true`:

```bash
# With the JAR file
java -Ddebug.env=true -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar /path/to/.env

# With Gradle
./gradlew runHiBobCli -PcliArgs="/path/to/.env" -Ddebug.env=true
```

## Example Output

```
HiBob CLI - 2025-03-24T12:34:56.789
-----------------------------------------------
Using token from command line arguments

Using HiBob API URL: https://api.hibob.com/v1

Fetching all employees...
Found 125 employees

First 10 employees (or fewer if less available):

Employee #1:
- Name: John Doe
- Email: john.doe@example.com
- Team: Engineering
- Title: Senior Software Engineer
- Manager: jane.smith@example.com

Employee #2:
- Name: Jane Smith
- Email: jane.smith@example.com
- Team: Engineering
- Title: Engineering Manager
- Manager: alex.johnson@example.com

[... more employees ...]

Employee Statistics:
- Teams (8): Design, Engineering, Finance, HR, Marketing, Operations, Product, Sales
- Titles: 42 unique titles
- Managers: 15 managers
```