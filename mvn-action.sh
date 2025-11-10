#!/bin/bash
set -e

# CRITICAL: Check JAVA_HOME validity, but don't unset if it's already correctly set in Dockerfile
# This must be done at the very beginning to prevent Java from trying to access
# invalid paths (e.g., macOS paths like /Users/.../Library/Java/... in Linux containers)
#
# The error "NoSuchFileException: /Users/.../Library/Java/..." occurs when JAVA_HOME
# is set to a macOS path in a Linux Docker container, and Java tries to access it
# before we can unset it.
if [ -n "$JAVA_HOME" ]; then
    # Check if JAVA_HOME points to a valid Java installation in the current environment
    if [ ! -d "$JAVA_HOME" ] || [ ! -f "$JAVA_HOME/bin/java" ]; then
        # JAVA_HOME is set but invalid (e.g., macOS path in Linux container)
        echo "WARNING: JAVA_HOME is set to invalid path: $JAVA_HOME"
        echo "Unsetting JAVA_HOME to use system default Java"
        unset JAVA_HOME
        # Also unset related Java environment variables that might cause issues
        unset JAVA_TOOL_OPTIONS
        unset _JAVA_OPTIONS
    elif [ -d "$JAVA_HOME" ] && [ -f "$JAVA_HOME/bin/java" ]; then
        # JAVA_HOME is valid, use it
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "Using existing JAVA_HOME: $JAVA_HOME"
    fi
else
    # JAVA_HOME is not set, set it to Java 21 (default for compilation)
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Setting JAVA_HOME to Java 21: $JAVA_HOME"
fi

# Start PostgreSQL if it's available and enabled
if command -v pg_ctl &> /dev/null && [ "${USE_LOCAL_POSTGRES:-false}" = "true" ]; then
    echo "Starting PostgreSQL..."
    
    # Create /run/postgresql directory for Unix socket lock files
    mkdir -p /run/postgresql
    chown -R postgres:postgres /run/postgresql
    
    # Ensure log directory exists and has correct permissions
    mkdir -p /var/lib/postgresql/data
    chown -R postgres:postgres /var/lib/postgresql/data
    
    # Check if PostgreSQL is already running
    if su-exec postgres pg_isready -U postgres &> /dev/null; then
        echo "PostgreSQL is already running."
    else
        # Clean up any stale lock files
        if [ -f /var/lib/postgresql/data/postmaster.pid ]; then
            echo "Removing stale postmaster.pid file..."
            rm -f /var/lib/postgresql/data/postmaster.pid
        fi
        
        # Check if data directory is initialized
        if [ ! -f /var/lib/postgresql/data/PG_VERSION ]; then
            echo "Initializing PostgreSQL data directory..."
            su-exec postgres initdb -D /var/lib/postgresql/data
            
            # Configure PostgreSQL for local connections (as postgres user)
            su-exec postgres sh -c 'echo "host all all 127.0.0.1/32 trust" >> /var/lib/postgresql/data/pg_hba.conf'
            su-exec postgres sh -c 'echo "host all all ::1/128 trust" >> /var/lib/postgresql/data/pg_hba.conf'
            su-exec postgres sh -c 'echo "local all all trust" >> /var/lib/postgresql/data/pg_hba.conf'
            su-exec postgres sh -c 'echo "listen_addresses=\"*\"" >> /var/lib/postgresql/data/postgresql.conf'
        fi
        
        # Start PostgreSQL with wait flag and check for errors
        if ! su-exec postgres pg_ctl -D /var/lib/postgresql/data -l /var/lib/postgresql/data/logfile start -w; then
            echo "ERROR: Failed to start PostgreSQL. Checking log file..."
            if [ -f /var/lib/postgresql/data/logfile ]; then
                cat /var/lib/postgresql/data/logfile
            fi
            exit 1
        fi
        
        echo "Waiting for PostgreSQL to be ready..."
        until su-exec postgres pg_isready -U postgres; do 
            sleep 1
        done
        echo "PostgreSQL is ready."
    fi
fi

if [ -d "${M2_HOME_FOLDER}" ]; then
     echo "INFO - M2 folder '${M2_HOME_FOLDER}' not empty. We therefore will beneficy from the CI cache";
     ls -l ${M2_HOME_FOLDER};
else
     echo "WARN - No M2 folder '${M2_HOME_FOLDER}' found. We therefore won't beneficy from the CI cache";
fi

echo "JAVA_HOME = $JAVA_HOME"
java -version

# Execute Maven command
# Only export JAVA_HOME and update PATH if JAVA_HOME is set
if [ -n "$JAVA_HOME" ]; then
    export JAVA_HOME
    export PATH="$JAVA_HOME/bin:$PATH"
else
    # If JAVA_HOME is not set, ensure it's not exported
    unset JAVA_HOME
fi
mvn -ntp $*

# Capture exit code
EXIT_CODE=$?

# Stop PostgreSQL if it was started
if command -v pg_ctl &> /dev/null && [ "${USE_LOCAL_POSTGRES:-false}" = "true" ]; then
    echo "Stopping PostgreSQL..."
    if su-exec postgres pg_isready -U postgres &> /dev/null; then
        su-exec postgres pg_ctl -D /var/lib/postgresql/data stop
    else
        echo "PostgreSQL is not running."
    fi
fi

exit $EXIT_CODE

