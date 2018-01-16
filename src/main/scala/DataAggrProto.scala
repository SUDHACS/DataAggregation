/**
  * Created by Kumar on 1/3/2018.
  */

package com.dhee

import org.apache.log4j.Logger
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.sql.streaming
import org.apache.spark.sql.streaming.Trigger

case class DealPnlData(busDate: String, dealId: String, prodId: String, portFolioId: String, scenarioId: Int, pnl: Float)

object DataAggrProto {
  def main(args: Array[String]) {

  var logger = Logger.getLogger(this.getClass())

  val jobName = "DataAggrProto"
//val connectionProperties = new Properties()
//connectionProperties.put("user", "postgres")
//connectionProperties.put("password", "postgres")

    val user="postgres"
    val pwd="postgres"
    val db="DATA_AGGR"
    val wh="<your warehouse name>"
    //val url="jdbc:snowflake://<account_name>.<region_id>.snowflakecomputing.com/?user="+user+"&password="+pwd+"&db="+db+"&warehouse="+wh
    val url = s"""jdbc:postgresql://localhost:5432/DATA_AGGR?user=postgres&password=postgres"""
    val writer = new JDBCSink(url, user, pwd)

    val spark = SparkSession.builder.
  master("local[2]")
  .appName("spark session example")
  .getOrCreate()

  import spark.implicits._
  val lines = spark
  .readStream
  .format("kafka")
  .option("kafka.bootstrap.servers", "localhost:9092")
  .option("subscribe", "test")
  .option("startingOffsets", "earliest")
  .load()
  .selectExpr("CAST(value AS STRING)")
  .as[String]

   val rawDataDF = lines.map(_.split(","))
                          .map(attr => DealPnlData(attr(0), attr(1),attr(2),attr(3),attr(4).trim.toInt,attr(5).trim.toFloat ) ).toDF()


    rawDataDF.createOrReplaceTempView("pnlStructTable")
    val aggDf = spark.sql("select portfolioId, scenarioId, sum(pnl) from pnlStructTable group by portfolioId, scenarioId")


    val query = aggDf
      .writeStream
      .foreach(writer)
      //.trigger(Trigger.ProcessingTime("30 seconds"))
      .outputMode("update") // could also be append or update
      .start()

  query.awaitTermination()

  }
}
