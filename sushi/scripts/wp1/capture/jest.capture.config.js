// Jest config for the capture run: SUSHI's own config + capture-setup.js; no coverage.
const SUSHI = process.env.WP1_SUSHI;
module.exports = {
  rootDir: SUSHI,
  moduleFileExtensions: ['js', 'ts'],
  transform: { '^.+\\.(js|jsx|ts|tsx)$': ['ts-jest', { tsconfig: SUSHI + '/test/tsconfig.json' }] },
  testMatch: ['**/test/**/*.test.(ts|js)'],
  testEnvironment: 'node',
  setupFiles: [__dirname + '/capture-setup.js'],
  setupFilesAfterEnv: ['jest-extended/all'],
  collectCoverage: false,
  preset: 'ts-jest'
};
