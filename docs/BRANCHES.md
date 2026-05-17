# Branch naming practices

We have three main branches: **master**, **staging** and **dev**. <br>

**master** is the main branch, all changes are pushed in here. <br>

## Feature branches

When you are working on a new feature, you should create a new branch from **dev**. <br>
The name of the branch should be:

feature/`<feature-name>` for new features, merged into master<br>
refactor/`<refactor-clas>` for new features, merged into master<br>
hotfix/`<hotfix-name>` for hotfixes merged into master<br>

For trivial features, directly commit into dev.

## Pull requests

When you are done with your feature, you should create a pull request from your feature branch into **dev**. <br>
The pull request should be reviewed by at least one other person before being merged. <br>

## Merging

When you are merging a pull request, you should always use the **Squash and merge** option. <br>