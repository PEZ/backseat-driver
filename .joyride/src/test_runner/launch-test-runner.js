const cp = require("child_process");
const path = require("path");
const process = require("process");
const {
  downloadAndUnzipVSCode,
  resolveCliArgsFromVSCodeExecutablePath,
  runTests,
} = require("@vscode/test-electron");

async function main(testWorkspace, isWatchMode) {
  console.log("launch-test-runner.js: testWorkspace", testWorkspace);
  try {
    const extensionTestsPath = path.resolve(__dirname, isWatchMode ? 'watchTests' : 'runTests');
    const vscodeExecutablePath = await downloadAndUnzipVSCode('insiders');
    const [cliPath, ...args] =
      resolveCliArgsFromVSCodeExecutablePath(vscodeExecutablePath);

    const launchArgs = [
      testWorkspace,
      ...args,
      // '--verbose',
      '--disable-workspace-trust',
      '--install-extension',
      'betterthantomorrow.joyride',
      '--force',
      '--install-extension',
      'betterthantomorrow.calva',
      '--force',
    ];

    console.log("launch-test-runner.js: launchArgs", launchArgs);
    cp.spawnSync(cliPath, launchArgs, {
      encoding: "utf-8",
      stdio: "inherit",
    });

    const runOptions = {
      vscodeExecutablePath,
      extensionTestsPath,
      launchArgs: [testWorkspace],
    };
    await runTests(runOptions)
      .then((_result) => {
        console.info("launch-test-runner.js: 👍 Tests finished successfully");
      })
      .catch((err) => {
        console.error("launch-test-runner.js: 👎 Tests finished with failures or errors:", err);
        process.exit(1);
      });
  } catch (err) {
    console.error("launch-test-runner.js: Failed to run tests:", err);
    process.exit(1);
  }
}

const workspace = path.resolve(__dirname, "../../..");
console.info(`launch-test-runner.js: Using Workspace: ${workspace}`);

const args = process.argv.slice(2);
const isWatchMode = args.length > 0 && args[0] === "--watch";

main(workspace, isWatchMode);

