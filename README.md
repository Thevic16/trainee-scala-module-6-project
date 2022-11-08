# Project Akka Restaurant Reviews.

# Dataset.
Description: This dataset contains reviews made by users of restaurants in all parts of the world.

link:
[Restaurant reviews dataset](https://www.kaggle.com/datasets/fahadsyed97/restaurant-reviews)

# Logic Diagram of the project.
[See documentation/diagram folder](documentation/diagram)

# Instruction to run the app.
On the resources/application-example, you will find an example of all the configurations expected for the application to
run. In order for the application to work, you should create your own resources/application using resources/application-example as a template.

-  On load-dataset you can specify the ``path-csv`` file for loading data purposes (download the CVS from the link
  provided in the previous section). Also, you can define ``run`` as ``true`` if you want to load the data from the 
  file and ``false`` otherwise. In the same segment, you can specify ``max-amount-row`` to limit the number of rows
  that will be loaded from the file (``-1`` means load all the rows) and ``chuck`` to select the number of rows that 
  loads in each interaction. 


- Next, you need to run `` docker-compose up`` to start up the containers [Cassandra database and projection database (Postgres)].
  When this process is completed you should run [init-schemas-scripts-cassandra.sql](db-scripts/init-schemas-scripts-cassandra.sql) 
  on Cassandra database container, if you are using IntelliJ you can use the database interface that brings to execute this scripts.


- Finally, you just need to run [RestaurantReviewApp.Scala](src/main/scala/com/vgomez/app/app/RestaurantReviewApp.scala) and the
  server will be online at http://localhost:8080/.

# URLs API description.
[See documentation/postman-collection](documentation/postman-collection)