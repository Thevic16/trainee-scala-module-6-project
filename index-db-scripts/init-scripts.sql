create schema "reviews";
create table if not exists "reviews"."Restaurant" ("index" BIGSERIAL NOT NULL PRIMARY KEY,"id" VARCHAR NOT NULL,"username" VARCHAR NOT NULL,"name" VARCHAR NOT NULL,"state" VARCHAR NOT NULL,"city" VARCHAR NOT NULL,"postalCode" VARCHAR NOT NULL,"latitude" DOUBLE PRECISION NOT NULL,"longitude" DOUBLE PRECISION NOT NULL,"categories" text [] NOT NULL,"timetable" VARCHAR NOT NULL);
create table if not exists "reviews"."Review" ("index" BIGSERIAL NOT NULL PRIMARY KEY,"id" VARCHAR NOT NULL,"username" VARCHAR NOT NULL,"restaurant_id" VARCHAR NOT NULL,"stars" INTEGER NOT NULL,"text" VARCHAR NOT NULL,"date" VARCHAR NOT NULL);
create table if not exists "reviews"."User" ("index" BIGSERIAL NOT NULL PRIMARY KEY,"username" VARCHAR NOT NULL,"password" VARCHAR NOT NULL,"role" VARCHAR NOT NULL,"latitude" DOUBLE PRECISION NOT NULL,"longitude" DOUBLE PRECISION NOT NULL,"favorite_categories" text [] NOT NULL);