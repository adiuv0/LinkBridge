import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptDir, '..');
const cmakePath = path.join(
  projectRoot,
  'node_modules',
  'sn-plugin-lib',
  'android',
  'src',
  'main',
  'cpp',
  'CMakeLists.txt',
);

if (fs.existsSync(cmakePath)) {
  let cmake = fs.readFileSync(cmakePath, 'utf8');
  let changed = false;

  if (!cmake.includes('project(SupernotePluginNative LANGUAGES C CXX)')) {
    cmake = cmake.replace(
      'cmake_minimum_required(VERSION 3.4.1)',
      'cmake_minimum_required(VERSION 3.4.1)\n\nproject(SupernotePluginNative LANGUAGES C CXX)',
    );
    changed = true;
  }

  if (!/^\s*c\+\+_shared\)?\s*$/m.test(cmake)) {
    cmake = cmake.replace('${log-lib})', '${log-lib}\n        c++_shared)');
    changed = true;
  }

  if (changed) {
    fs.writeFileSync(cmakePath, cmake, 'utf8');
    console.log('Patched Supernote SDK native C++ linkage.');
  } else {
    console.log('Supernote SDK native C++ linkage is already patched.');
  }
} else {
  console.warn('Supernote SDK CMake file was not found; native linkage patch skipped.');
}

const verifyUtilsPaths = [
  path.join(projectRoot, 'node_modules', 'sn-plugin-lib', 'src', 'sdk', 'utils', 'VerifyUtils.ts'),
  path.join(projectRoot, 'node_modules', 'sn-plugin-lib', 'lib', 'module', 'sdk', 'utils', 'VerifyUtils.js'),
];

let removedSensitiveLogging = false;
for (const verifyUtilsPath of verifyUtilsPaths) {
  if (!fs.existsSync(verifyUtilsPath)) continue;
  const original = fs.readFileSync(verifyUtilsPath, 'utf8');
  const sanitized = original.replace(
    /^\s*console\.log\('verifyParams', schema, params, options\);\r?\n/m,
    '',
  );
  if (sanitized !== original) {
    fs.writeFileSync(verifyUtilsPath, sanitized, 'utf8');
    removedSensitiveLogging = true;
  }
}

if (removedSensitiveLogging) {
  console.log('Removed sensitive Supernote SDK parameter logging.');
} else {
  console.log('Sensitive Supernote SDK parameter logging is already removed.');
}
