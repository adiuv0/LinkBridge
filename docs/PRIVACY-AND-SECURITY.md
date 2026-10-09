# Privacy and security

## Data flow

LinkBridge transfers the shared URL and article title directly from the Android phone to the Supernote over a short-lived Bluetooth Low Energy connection. It does not use a relay, cloud service, shared LAN, analytics service, or LinkBridge account.

The Android app has no internet permission. Pending links are stored in the app's private local preferences until the Supernote confirms that the note was saved. Android backup is disabled for the app.

The Supernote stores only recent delivery identifiers for duplicate prevention. It does not keep a second URL inbox after insertion. The URL itself becomes part of the destination `.note` file, as intended, and can later be transferred by the user's existing Partner-app workflow.

Release packaging removes an upstream Supernote SDK debug statement that would otherwise print API parameters such as URLs, titles, and note paths to the device log.

## Current public-beta limitation

LinkBridge 0.5.0 does not yet authenticate the nearby phone at the application layer. The service is advertised only while a link is waiting, and the Supernote scans only while its LinkBridge action is open, which keeps the exposure window short. Nevertheless, a sufficiently close device could theoretically impersonate one side, inject a link, or observe an unprotected Bluetooth session.

Since 0.5.1 the phone accepts a save confirmation only from the device that read the link's URL, so a second nearby device can no longer make the phone delete a link it never delivered. This narrows the risk but is not authentication: a device that impersonates the Supernote end to end can still read and confirm a link.

Do not use the current beta for secret, credential-bearing, medical, legal, or otherwise sensitive URLs in a hostile radio environment. Authenticated pairing and encrypted sessions are required before calling LinkBridge a stable public release.

## Responsible reporting

Report suspected security problems through the repository's private vulnerability-reporting flow described in [SECURITY.md](../SECURITY.md). Do not post exploit details in a public issue.
