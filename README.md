# TalpaTask

Before run, make sure that you have Docker and docker-compose properly installed

to run, follow the commands:

```
git clone https://github.com/gsjunior86/TalpaTask
cd TalpaTask
docker-compose up
```

The input file is referenced by the Environment Variable **input_file**, as defined in the docker-compose.yaml file.

When started, the docker-composed command will start the Processing Job image and a Postgres Image. A database called "talpa" will be created.
You can access the database in the localhost (127.0.0.1) address through the port 5432, user/password talpa/123456.

In the end, two tables will be generated with the resulting data: **average_speed** and **activity**

# Building locally
If you want to build the image locally, make sure that you have JDK1.8 and Maven installed.

then, you can build with:
```
make build dockerize
```
