const vscode = require('vscode');

exports.run = async () => {
  console.log("runTests.js: run started");
  await vscode.commands.executeCommand('calva.activateCalva');
  console.log("runTests.js: calva activated, executing joyride.runCode to run tests");
  console.log(`runTests.js: current workspace root: ${vscode.workspace.workspaceFolders[0].uri.fsPath}`);
  return vscode.commands.executeCommand(
    'joyride.runCode',
    `(require '[test-runner.runner :as runner])
     (require '[test.config :as config])
     (runner/run-ns-tests!+ config/namespaces)`
  );
};
