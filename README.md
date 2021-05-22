# BedrockConnect - CHS + DNS

![Logo](https://i.imgur.com/H9zVzGT.png)

For the original project, please take a look at [Pugamatt/BedrockConnect](https://github.com/Pugmatt/BedrockConnect)

This project translated all texts into Chinese, and embedded a DNS server into the Java application, so external DNS setup is not required.
The DNS server will listen on port 53 by default. This may require root privileges.

# DNS Configuration
The DNS entries has been hardcoded. here are the parameters (or environment variables) you may need to turn this feature on.
Arguments will override environment variables.

|   Argument    | Environment variable |                              Description                               |  Datatype  |  Default Value  |
| ------------- | -------------------- | ---------------------------------------------------------------------- | ---------- | --------------- |
| dns-on        | BC_DNS_ON            | Turn the DNS on or off                                                 | True/False | False           |
| dns-ip        | BC_DNS_IP            | Which IP should the DNS redirect to. Commonly your server's public IP. | IP Address | 104.238.130.180 |
| dns-recursive | BC_DNS_RECURSIVE     | Whether non-local entries shall be recursively looked                  | True/False | True            |
| dns-cache     | BC_DNS_CACHE         | The number of DNS Entries to cache                                     | Integer    | 1000            |