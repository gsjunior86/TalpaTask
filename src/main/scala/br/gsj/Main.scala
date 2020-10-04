package br.gsj

import java.io.File

import scala.sys.process._

import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import scala.collection.mutable.ListBuffer
import org.apache.spark.sql.types.LongType

/**
 *
 * Data Processing for Talpa Solutions data.
 *
 */

object Main {

  //environment variables
  val DB_ENV = "db_host"
  val PY_ENV = "base_py"
  val CSV_ENV = "input_file"
  val OUTPUT_ENV = "output_folder"

  //postgres driver
  val driver = "org.postgresql.Driver"

  var py_pred = "python " + System.getenv(PY_ENV) + "/Prediction.py"
  var py_res = System.getenv(PY_ENV) + "/result.csv"

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName("TalpaTest").master("local[*]").getOrCreate()
    import spark.sqlContext.implicits._

    val db_host = System.getenv(DB_ENV)

    val csv_file = System.getenv(CSV_ENV)

    val csv_output = System.getenv(OUTPUT_ENV)

    //user defined function to compute average between two columns
    val avgUDF = udf((v1: Float, v2: Float) => { (v1 + v2) / 2 })
    
    val rawCsv = spark.read
      .option("header", true)
      .option("inferSchema", "true")
      .csv(csv_file)
      
      val e = SchemaValidator.isSchemaValid(rawCsv)
      
      if(!e.isEmpty())
        throw new IllegalArgumentException("Input file is invalid, missing columns: " + e)
      

    //compute average between
    val csv = spark.read
      .option("header", true)
      .option("inferSchema", "true")
      .csv(csv_file)
      .na
      .fill(0, Array(
        "pvalve_drill_forward",
        "hydraulic_pump",
        "bolt",
        "boom_lift",
        "boom_lower",
        "boom_forward",
        "boom_backward",
        "drill_boom_turn_left",
        "drill_boom_turn_right",
        "drill_boom_turn_forward",
        "drill_boom_turn_backward",
        "beam_right",
        "beam_left",
        "anchor"))
      //.withColumn("timestamp", to_timestamp(col("timestamp")) )
      .withColumn("boom_long", avgUDF(col("boom_lift"), col("boom_lower")))
      .withColumn("boom_lati", avgUDF(col("boom_forward"), col("boom_backward")))
      .withColumn("drill_boom_long", avgUDF(col("drill_boom_turn_left"), col("drill_boom_turn_right")))
      .withColumn("drill_boom_lati", avgUDF(col("drill_boom_turn_forward"), col("drill_boom_turn_backward")))
      .withColumn("beam", avgUDF(col("beam_left"), col("beam_right")))

    //select only the columns that will be used by the model (timestamp is excluded on the py script)
    val py_df = csv.select(
      "timestamp",
      "engine_speed", "hydraulic_drive_off", "drill_boom_in_anchor_position", "pvalve_drill_forward", "bolt", "boom_long", "boom_lati", "drill_boom_long", "drill_boom_lati", "beam")

    //saves the output for prediction locally
    py_df.coalesce(1).write
      .option("header", true)
      .mode(SaveMode.Overwrite)
      .csv("result")

    //computes the average speed
    var avg_df = csv.withColumn("timestamp", to_timestamp(col("timestamp"))).groupBy(window($"timestamp", "5 minutes"))
      .agg(round(avg(col("engine_speed")), 2).as("avg_speed")).withColumn("start", col("window.start"))
      .withColumn("end", col("window.end")).drop("window")
      .select("start", "end", "avg_speed")

    avg_df.show

    avg_df.printSchema()

    //saves the average speed data as csv file
    avg_df.coalesce(1).write
      .option("header", true)
      .mode(SaveMode.Overwrite)
      .csv(csv_output + "/average_speed")

    //tries to save on the postgres DB. If something happpens an exception will be thrown
    
    var conf_avgspd = false
    var conf_activity = false
      
    try {
      avg_df.write.format("jdbc").mode(SaveMode.Overwrite)
        .option("url", db_host)
        .option("dbtable", "average_speed")
        .option("user", "talpa")
        .option("password", "123456")
        .option("driver", driver)
        .save
        
       conf_avgspd = true
       

    } catch {
      case e: Throwable => e.printStackTrace
    }

    //calls python scipt passing the output file
    callPython(getListOfFiles("result"))

    //load the result from the prediction
    val res_file = new File(py_res)

    //if the file does not exist, an error occured in the prediction
    if (res_file.exists()) {

      var pred_df = spark.read
        .option("header", true)
        .option("inferSchema", "true")
        .csv(py_res)
        .withColumn("timestamp", to_timestamp(col("timestamp")))
        .withColumnRenamed("prediction", "type")
        .select("timestamp", "type")
        .orderBy("timestamp")

      val ndf = pred_df.rdd.map(f => (f(0).toString, f(1).toString, "")).collect

      var typ = ndf(0)._2
      var cont = 1

      


      var na = new ListBuffer[(String, String, String)]()
     //computes the activity id
      for (el <- ndf) {
        if (!el._2.equals(typ)) {
          cont += 1
          typ = el._2
        }

        na += ((el._1, el._2, cont.toString))

      }
           //creates a new dataframe with the computed activity id
      pred_df = spark.sparkContext.parallelize(na).toDF("timestamp", "type", "id")
        .withColumn("timestamp", to_timestamp(col("timestamp")))
        .withColumn("id", col("id").cast(LongType))

      //tries to save on the postgres DB. If something happpens an exception will be thrown

      try {

        pred_df.write.format("jdbc").mode(SaveMode.Overwrite)
          .option("url", db_host)
          .option("dbtable", "activity")
          .option("user", "talpa")
          .option("password", "123456")
          .option("driver", driver)
          .save
          
          conf_activity = true

      } catch {
        case e: Throwable => e.printStackTrace
      }

      //saves the activity data as csv file

      pred_df.coalesce(1).write
        .option("header", true)
        .mode(SaveMode.Overwrite)
        .csv(csv_output + "/activity")

    } else {
      println("An error occurend when running the python script!")
    }
    //delete the prediction file, since it is useless at this point
    res_file.delete
    
   
    
    if(conf_avgspd && conf_activity)
      println("All data were successfully saved on Database!")
      
    

  }

  def callPython(file: String): Unit = {
    val cmd = py_pred + " " + file
    println("CMD >>>> " + cmd)
    val result = cmd ! ProcessLogger(stdout append _, stderr append _)
    println(result)
    println("stdout: " + stdout)
    println("stderr: " + stderr)
  }

  def getListOfFiles(dir: String): String = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {

      d.listFiles.filter(_.getName.endsWith(".csv")).toList(0).getAbsolutePath

    } else {
      ""
    }
  }
}