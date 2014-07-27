Azkaban2
========

### 1. Add a lock jobtype to prevent some jobs on different workflows from running at the same time.

Examples:

```
lock:
		type=lock              //jobtype
		dependencies=foo
		lock=lockTest          //lock name
```
```
unlock:
		type=lock              //jobtype
		dependencies=bar
		unlock=lockTest        //lock name
```

Attentions:

Lock job and unlock job should be used in pairs.

The workflow will release all its locks before quitting no matter it fails or succeeds.


###  2. Add two buttons to change the state of the shedule (disabled or enabled)

When we need to change some jobs of the workflow, we could pause its scheduler until we make sure the changes are right.


### 3. Disable scheduls when workflows fails some times continuously to avoid receiving massive failure emails.

It is very useful when the workflow is easy to be failed and the scheduling interval is short, the times will be set when we set schedules.


# 4. Provide CRUD (create, retrieve, update, delete) operations for workflows from web portal.

It is complex and unintuitive when we create, retrieve or update the workflows. for example, when we need to add a job to the present workflow, we need to create a file to describe the job, make a zip file to contain the whole workflow, upload the zip file to test whether it works well or not, and we need to repeat the process if it is wrong.

But now we could create, retrieve and update workflows easily and friendly from azkaban portal.


Documentation
-------------

For Azkaban documentation, please go to [Azkaban Project Site](http://azkaban.github.io)

For help, please visit the Azkaban Google Group: [Azkaban Group](https://groups.google.com/forum/?fromgroups#!forum/azkaban-dev)

