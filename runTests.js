const vscode = require('vscode');

exports.run = async () => {
  console.log("runTests.js: run started");
  await vscode.commands.executeCommand('calva.activateCalva');
  console.log("runTests.js: calva activated, executing joyride.runCode to run tests");
  return vscode.commands.executeCommand(
    'joyride.runCode',
    `(require '[test-runner.runner :as runner])
     (runner/run-tests!+ "Waiting for workspace to activate...")`
  );
};
