#!/usr/bin/env python3

import re

# Read the OpenAPI file
with open('openapi.yaml', 'r') as f:
    content = f.read()

# Remove the TtlockDevice endpoints
pattern = r'  /admin/spaces/\{space-id\}/ttlock-devices:.*?(?=  /|\Z)'
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove any remaining TtlockDevice references
content = re.sub(r'TtlockDevice', 'DigitalLock', content)

# Write the cleaned content back
with open('openapi.yaml', 'w') as f:
    f.write(content)

print("OpenAPI spec cleaned successfully!")