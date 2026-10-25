# Contributing to GlossHub

Thank you for helping. There are two ways in.

## Feedback

You don't need to know any code to help: tell us what was confusing,
missing or wrong. Open an issue with the **Feedback** form (the *Send
feedback* link at the bottom of every page of the site leads there). The
version shown at the bottom of the page, your browser, and the source of
the text you were working on (on its page: *Edit*, then *Document
source*) all help.

## Changes to the code

1. Read [docs/development.md](docs/development.md): setting up, the
   commands, the tests, the conventions, and the process for changes.
   [docs/architecture.md](docs/architecture.md) explains how the code
   works.
2. For anything bigger than a small fix, open an issue first, so the
   change can be agreed before you write it. A change to the input
   format always starts as an issue: texts written today must mean the
   same thing tomorrow ([docs/format.md](docs/format.md), §2).
3. Work on a branch, keep each commit building and passing the tests, and
   open a pull request into `main`. The template asks what changed, why,
   and how it was tested; `npm test` must pass.

By contributing, you agree that your contribution is licensed under the
project's [MIT License](LICENSE).
