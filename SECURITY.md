# Security policy

## Supported versions

LinkBridge is currently a public beta. Security fixes are provided only for the latest published beta release.

| Version | Supported |
| --- | --- |
| 0.5.x | Yes |
| Earlier development builds | No |

## Reporting a vulnerability

Please use [GitHub private vulnerability reporting](https://github.com/adiuv0/LinkBridge/security/advisories/new). Do not open a public issue for a suspected vulnerability.

Include the affected LinkBridge versions, phone and Supernote models, operating-system or firmware versions, and reproducible steps using a non-sensitive test URL. Remove device addresses, account details, private note names, and real browsing data from screenshots or logs.

The project is maintained on a best-effort basis. A report will be acknowledged as soon as practical, investigated privately, and disclosed publicly only after a fix or mitigation is available.

## Current beta boundary

Version 0.5.0 does not provide application-level phone authentication or encryption beyond the underlying Bluetooth connection. Its short-lived advertising window reduces exposure but does not eliminate nearby-device impersonation or observation risks. Do not use the beta for sensitive or credential-bearing URLs in a hostile radio environment.
