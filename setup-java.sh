#!/bin/bash
# Setup Java 21 for this project
# This script can be sourced: source setup-java.sh
# Or executed: ./setup-java.sh

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Java 21 path (priority)
JAVA_21_HOME=/Users/qcastel/Library/Java/JavaVirtualMachines/temurin-21.0.5/Contents/Home

# Check if Java 21 exists and use it, otherwise try .env
if [ -d "$JAVA_21_HOME" ] && [ -f "$JAVA_21_HOME/bin/java" ]; then
    export JAVA_HOME="$JAVA_21_HOME"
    echo "Using Java 21 from: $JAVA_HOME"
else
    # Load .env file if it exists
    if [ -f "$SCRIPT_DIR/.env" ]; then
        export $(grep -v '^#' "$SCRIPT_DIR/.env" | xargs)
    fi
    
    # Fallback to hardcoded path if .env doesn't have JAVA_HOME
    if [ -z "$JAVA_HOME" ]; then
        export JAVA_HOME="$JAVA_21_HOME"
    fi
fi

export PATH=$JAVA_HOME/bin:$PATH

echo "Java environment configured:"
echo "JAVA_HOME: $JAVA_HOME"
java -version

