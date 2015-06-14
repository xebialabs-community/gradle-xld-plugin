#!/usr/bin/env bash
#
# THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
# FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
#


# This script initializes "local" environment in default XL Deploy
# instance running on http://localhost:4516.
# "xld-cli" is supposed to be an alias for XL Deploy CLI script.

SCRIPT_PATH=$(cd `dirname $0` && pwd)

xld-cli -username admin -password admin -f "$SCRIPT_PATH/init_xld_test_environment.py"
