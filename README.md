## Azkaban2

[![Build Status](https://travis-ci.org/azkaban/azkaban2.png?branch=master)](https://travis-ci.org/azkaban/azkaban2)

For Azkaban documentation, please go to
[Azkaban Project Site](http://azkaban.github.io/azkaban2/)
There is a google groups: [Azkaban Group](https://groups.google.com/forum/?fromgroups#!forum/azkaban-dev)


1. add a lock jobtype to prevent some jobs on different workflows from running at the same time.
example:
lock:
type=lock              //jobtype
dependencies=foo
lock=lockTest         //lock name

unlock:
type=lock              //jobtype
dependencies=bar
unlock=lockTest        //lock name


Attentions:
lock and unlock should be used in pairs
workflow will release all its locks before quitting no matter it fails or succeeds


2. add a function to change the state of shedule (disabled or enabled)
there are two buttons to disable or enable schedules.

todo list:
disable scheduls when workflows fails some times continuously.
enable CIUD workflow from web portal

