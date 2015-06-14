#
# THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
# FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
#

localhost = repository.create(factory.configurationItem("Infrastructure/localhost", "overthere.LocalHost", {"os": "UNIX"}))
local = repository.create(factory.configurationItem("Environments/local", "udm.Environment", {"members": [localhost.id]}))
