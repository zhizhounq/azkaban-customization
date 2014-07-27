Azkaban2
========

		1. add a lock jobtype to prevent some jobs on different workflows from running at the same time.
		example:
```
lock:
type=lock              //jobtype
dependencies=foo
lock=lockTest         //lock name

unlock:
type=lock              //jobtype
dependencies=bar
unlock=lockTest        //lock name

```
		Attentions:
		lock and unlock should be used in pairs
		workflow will release all its locks before quitting no matter it fails or succeeds


		2. add a function to change the state of shedule (disabled or enabled)
		there are two buttons to disable or enable schedules, when we need to change some jobs of the workflow, we could pause its scheduler until we make sure the changes are right.


		3. disable scheduls when workflows fails some times continuously.
		it is very useful when the workflow is easy to be failed and the executing interval is short, the times will be set when we set schedule.


		4. provide CRUD (create, retrieve, update, delete) operations for workflows from web portal.
		it is complex and unintuitive when we create, retrieve or update the workflows. for example, when we need to add a job to present workflow, we need to create a file to describe the job, make a zip file to contain the whole workflow, upload the file to test whether it works well or not, and we need to repeat the process if it is wrong.
now we could create, retrieve and update workflows easily and friendly.

Documentation
-------------

For Azkaban documentation, please go to [Azkaban Project Site](http://azkaban.github.io)

For help, please visit the Azkaban Google Group: [Azkaban Group](https://groups.google.com/forum/?fromgroups#!forum/azkaban-dev)

