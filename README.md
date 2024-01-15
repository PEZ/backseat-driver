## Backseat Driver

The Hackable VS Code AI Assistant.

## What is this?

**Backseat Driver** is a ChatGPT API client for VS Code, similar in concept to the chat part of the **GitHub CoPilot** extension, but much, much simpler.

It best supports [Clojure](https://clojure.org) and [ClojureScript](https://clojurescript.org) coding, but can assist with anything code crafting related. Besdies. And it's a [Joyride](https://github.com/BetterThanTomorrow/joyride) script, so _you can hack in support_ for your language of choice.

WIP: Or even just a proof of concept for now. But it works!

https://github.com/PEZ/backseat-driver/assets/30010/57c4922d-495c-4ad0-9b61-2767f64e37f1


To use it you will need:

* VS Code
* The [Joyride](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.joyride) extension
* The [Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva) extension (Backseat Driver depends on some [Calva API:s](https://calva.io/api/))
* The `npm` command line tool (probably you want to have Nodejs installed, even if Nodejs is not a direct dependency for the script)
* An OpenAI API key available in your environment as `OPENAI_API_KEY`
  * As per the [Setup your API key for all projects](https://platform.openai.com/docs/quickstart/step-2-setup-your-api-key) on the OpenAI Platform site.

## Install

Backseat Driver is meant to be a global (User) Joyride script, so that it is available in all your projects. But you can try it out as a local (Workspace) script:

1. Open this project in VS Code
1. Install Nodejs dependencies:
   ```sh
   npm i
   ```
1. Reload the VS Code window (from the Command Palette: **Developer: Reload Window**)
1. Create a keyboard shortcut to run the Backseat Driver assistant. This registers <kbd>ctrl</kbd>+<kbd>alt/option</kbd>+<kbd>,</kbd> as the shortcut:
   ```json
    {
        "key": "ctrl+alt+,",
        "command": "joyride.runCode",
        "args": "(backseat Driver.app/please-advice!)",
    },
   ```

TBD: Instructions for how to use Backseat Driver as a global/user script.

## Usage

Backseat Driver does not automatically edit your code, or even insert suggestions. At least for now it is more similar to having the ChatGPT chat in VS Code, which is aware of your code context. You ask Backseat Driver for assistance, and it will answer based on your question + the code you are editing.

Your conversation with Backseat Driver will be printed in the **Backseat Driver** output channel. (More fancy UI is being planned).

To ask Backseat Driver for advice, press <kbd>ctrl</kbd>+<kbd>alt/option</kbd>+<kbd>,</kbd>. You should expect it to work like in the demo video above.

### The Backseat Driver Menu

In the status bar you will have a button titled **Backseat Driver** which opens up the Backseat Driver menu. It lets you:

* Ask the AI for assistance
* Start a new chat session
* Show the output channel

### Tips

In the demo above I have placed the Backseat output channel in the **Secondary Sidebar**. (Search for it in the Command Palette if you're not in the know).

To show the channel at will you can bind a keyboard shortcut, like so:

```json
    {
        "key": "ctrl+alt+a ctrl+alt+a",
        "command": "joyride.runCode",
        "args": "(backseat.ui/show-channel!)",
    },
```

NB: Since it's Joyride you can script yourself a menu or a statusbar button for things like this too.

## Happy Backseat assisted coding!

Issues welcome.

Since this is a Joyride script, it is meant that you adapt it to your needs. If you add things that you think are generally useful, please don't hesitate to tell me about it in an issue, and we can discuss about if it should be a pull request on this repo.

See typos? Please PR.

See bugs? Please Issue (and please PR too!)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.