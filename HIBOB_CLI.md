# HiBob CLI - Command Line Interface

This tool provides a command-line interface for fetching and displaying employee information from the HiBob API.

## Building the CLI

To build the standalone JAR file:

```bash
./gradlew hibobCliJar
```

This will create a JAR file in the `build/libs/` directory.

## Configuration

### Email Mapping Configuration

The HiBob CLI now supports mapping personal email addresses to corporate identities via the `email_mappings.json` file.

#### File Format

The email mappings file is a simple JSON document that maps non-standard emails to their standard corporate counterparts:

```json
{
  "personal@gmail.com": "developer@company.com",
  "old.email@hotmail.com": "jane.smith@company.com"
}
```

#### Default Locations

The system looks for email mappings in the following locations, in order:

1. The location specified via the `--mappings-file` CLI argument
2. A project-specific file at `[project_directory]/email_mappings.json`
3. A user-specific file at `~/.commitmapper/email_mappings.json`

#### Email Mapping Rules

1. If a mapping exists for an email address, the mapped address is used for HiBob lookups
2. If no mapping exists, the original address is used as-is
3. Email lookups in HiBob are case-insensitive
4. Mappings are applied before any HiBob API calls are made

### Authentication

You can provide your HiBob API token in several ways:

1. Command line argument: `token=YOUR_TOKEN`
2. Environment variable: Set `HIBOB_API_TOKEN` environment variable
3. .env file: Create a file with `HIBOB_API_TOKEN=YOUR_TOKEN` and provide the path to the file

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

# Use a .env file for credentials
./gradlew runHiBobCli -PcliArgs="/path/to/.env"

# Specify a custom mappings file
./gradlew runHiBobCli -PcliArgs="--mappings-file=/path/to/email_mappings.json token=YOUR_TOKEN"
```

### Using JAR File

After building the JAR file, you can run it directly:

```bash
# List all employees
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar token=YOUR_TOKEN

# Fetch a specific employee by email (with automatic email mapping)
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar user@example.com token=YOUR_TOKEN

# Use a .env file for credentials
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar /path/to/.env
```

### New Command-Line Options

```
java -jar hibob-cli.jar [options]

Options:
  --mappings-file=PATH         Path to the email mappings JSON file
  --add-mapping=FROM:TO        Add an email mapping (FROM → TO)
  --remove-mapping=EMAIL       Remove a mapping for the specified email
  --list-mappings              List all current email mappings
  token=TOKEN                  HiBob API token (overrides .env file)
  url=URL                      HiBob API URL (default: https://api.hibob.com/v1)
```

### Email Mapping Management

```bash
# Add a mapping from personal@gmail.com to work@company.com
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar --add-mapping=personal@gmail.com:work@company.com

# Remove the mapping for personal@gmail.com
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar --remove-mapping=personal@gmail.com

# List all current mappings
java -jar build/libs/hibob-cli-1.0-SNAPSHOT.jar --list-mappings
```

## Sample Email Mappings File

Here's a sample `email_mappings.json` file:

```json
{
  "personal@gmail.com": "developer@company.com",
  "old.email@hotmail.com": "jane.smith@company.com",
  "john.personal@outlook.com": "john.doe@company.com",
  "freelancer1234@gmail.com": "consultant@company.com",
  "legacy.account@yahoo.com": "team.lead@company.com"
}
```

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
Email mappings loaded from: /project/email_mappings.json (5 mappings)

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