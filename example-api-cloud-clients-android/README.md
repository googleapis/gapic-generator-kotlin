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
    + [Enable Stackdriver Logging API](https://console.cloud.google.com/apis/library/logging.googleapis.com)
1. [Create a new service account](https://console.cloud.google.com/apis/credentials/serviceaccountkey) with a JSON keyfile
1. Move the service account JSON file to `app/src/main/res/raw/sa.json`

After your Google Cloud project is setup you are ready to run the examples. Each example is a single
`Activity` that calls an API and puts a stringified version of the result on the UI. 

To switch between examples, modify the sample application's manifest `app/src/main/AndroidManifest.xml` 
and change the main activity (i.e. move the `intent-filter` under the example that you want to run).

*Note:* You should not normally put a service account keyfile in an application that will be distributed.
It is done in these examples for simplicity but not should not be done in real applications.
