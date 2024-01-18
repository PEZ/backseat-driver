const cp = require("child_process");
const path = require("path");
const process = require("process");
const {
  downloadAndUnzipVSCode,
  resolveCliArgsFromVSCodeExecutablePath,
  runTests,
} = require("@vscode/test-electron");

async function main(testWorkspace) {
  try {
    const extensionTestsPath = path.resolve(__dirname, 'runTests');
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

    console.log("launchArgs", launchArgs);
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
        console.info("Tests finished");
      })
      .catch((err) => {
        console.error("Tests finished:", err);
        process.exit(1);
      });
  } catch (err) {
    console.error("Failed to run tests:", err);
    process.exit(1);
  }
}

main(".");

