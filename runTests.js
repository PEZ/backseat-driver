const vscode = require('vscode');

exports.run = async () => {
  await vscode.commands.executeCommand('calva.activateCalva');
  return vscode.commands.executeCommand(
    'joyride.runCode',
    `(require '[test-runner.runner :as runner])
     (require '[test.config :as config])
     (runner/run-ns-tests!+ config/namespaces)`
  );
};
