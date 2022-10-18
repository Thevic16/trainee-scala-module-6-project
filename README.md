# Project Akka Restaurant Reviews.

# Link dataset.
https://www.kaggle.com/datasets/fahadsyed97/restaurant-reviews

# Logic Diagram of the project.
See documentation/diagram folder

# Instruction to run the app.
On the resources/application-example, you will find an example of all the configurations expected for the application to
run.

- You can choose between running the app with LocalStorage or Cassandra by changing the path option on actor-system-config
 (put "localStores" or "cassandra"). In the same segment, you can configure the desired timeout limit.

- On the other hand, on load-dataset you can specify the path-csv file for loading data purposes. Also, you can define
  run as true if you want to load the data from the file and false otherwise. In the same segment, you can specify 
  max-amount-row to limit the number of rows that will be loaded from the file (-1 means load all the rows) and chuck
  to select the number of rows that loads in each interaction. 

# URLs API description.
See documentation/postman-collection