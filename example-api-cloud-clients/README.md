# Kotlin Client Examples

Examples of using the Kgen code generator to generate Google Cloud client libraries in Kotlin.

## Running

To run the samples in this repository you must have a Google Cloud project that you can
use. To get started:

1. [Create a project](https://cloud.google.com/resource-manager/docs/creating-managing-projects), if you don't already have one
1. [Enable billing for your project](https://cloud.google.com/billing/docs/how-to/modify-project#enable_billing_for_a_new_project)
1. Enable the APIs that the examples use:
    + [Enable Cloud Speech API](https://console.cloud.google.com/apis/library/speech.googleapis.com)
    + [Enable Cloud Natural Language API](https://console.cloud.google.com/apis/library/language.googleapis.com)
    + [Enable Cloud Vision API](https://console.cloud.google.com/apis/library/vision.googleapis.com)
    + [Enable Cloud PubSub API](https://console.cloud.google.com/apis/library/pubsub.googleapis.com)
    + [Enable Stackdriver Logging API](https://console.cloud.google.com/apis/library/logging.googleapis.com)
1. [Create a new service account](https://console.cloud.google.com/apis/credentials/serviceaccountkey) with a JSON keyfile
1. Define an environment variable so the example application uses your service account credentials:
    ```bash
    $ export GOOGLE_APPLICATION_CREDENTIALS=<path to your service account JSON file>
    $ export PROJECT=<name_of_your_gcp_project>
    ```
1. Run via gradle:
    ```bash
    $ ./gradlew run --args language
    ```

To switch between examples modify the flag passed to the `--args` parameter. 
