# Project Akka Restaurant Reviews.

# Project Task/Challenge.
As a big data project, the challenge was to select a dataset with a least 1 Gb of information and 15 columns and with good business logic to work with.
 
In this case, the selected dataset was a restaurant dataset that analyzed reviews of users of restaurants before and after covid-19 pandemic.

From the previously mentioned dataset, it was required to create API REST that allows registering users, and restaurants and also creating reviews about the restaurants. Additionally, other services that were indispensable were the recommendations of restaurants by categories, by favorite categories of a specific user, and also by location.

# Project Solution.
The solution of the project was implemented in Scala language using the Akka toolkit as the base core to come up with the final outcome.

The Actors are divided into readers and writers which allows them to separate responsibilities for better performance.

Another important decision that was taken was to use Akka Stream to load the data from the dataset to the application, as the library is designed to process elements (in this case the rows from the dataset) in an asynchronous, non-blocking backpressure way, this was considered the right tool for the job.

One of the most important aspects to highlight is the use of akka projection to project the events in an index database (in this case PostgreSQL) so when the users request information about the restaurant is more efficient to take the information from this database than looking and filter the events from the Cassandra database.

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
