package br.gsj

import org.apache.spark.sql.Row
import org.apache.spark.sql.Dataset
import scala.util.control.Breaks._



object SchemaValidator {
  
  val expectedCols = Array("_c0","timestamp","engine_speed","hydraulic_drive_off"
      ,"drill_boom_in_anchor_position","pvalve_drill_forward","hydraulic_pump",
      "bolt","boom_lift","boom_lower","boom_forward","boom_backward","drill_boom_turn_left"
      ,"drill_boom_turn_right","drill_boom_turn_forward","drill_boom_turn_backward","beam_right"
      ,"beam_left","anchor","activity")
  
  def isSchemaValid(df: Dataset[Row]): String = {
    
    val cols = df.columns
    var missingColumns = new StringBuilder("")
    for(s <- expectedCols){
      if (!(cols contains s))
        missingColumns.append(s)
      
    }
    
   missingColumns.toString
  }
  
}