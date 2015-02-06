package tsdb.util.gui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import tsdb.TimeConverter;
import tsdb.raw.TimestampSeries;
import tsdb.raw.TsEntry;
import tsdb.util.Pair;
import tsdb.util.gui.TimeSeriesPainter.PosHorizontal;
import tsdb.util.gui.TimeSeriesPainter.PosVerical;

public class TimeSeriesHeatMap {

	private final TimestampSeries ts;

	public TimeSeriesHeatMap(TimestampSeries ts) {
		this.ts = ts;
		/*if(ts.timeinterval!=60) {
			throw new RuntimeException("TimeSeriesHeatMap needs one hour time steps: "+ts.timeinterval);
		}*/
	}

	public void draw(TimeSeriesPainter tsp, String sensorName) {
		setRange(tsp,sensorName);
		tsp.setColorRectWater();
		long start = ts.entryList.get(0).timestamp-ts.entryList.get(0).timestamp%(60*24);
		for(TsEntry entry:ts.entryList) {
			float value = entry.data[0];
			if(!Float.isNaN(value)) {
				tsp.setIndexedColor(value);
				float x = (((entry.timestamp-start)/60)/24)*1;
				float y = (((entry.timestamp-start)/60)%24)*1;
				tsp.drawLine(x, y, x, y);
				//tsp.fillRect(x, y, x+4, y+4);
			}
		}
	}

	public void drawTimescale(TimeSeriesPainterGraphics2D tsp, float xMin, float yMin, float xMax, float yMax) {
		tsp.setColor(255, 255, 255);
		tsp.fillRect(xMin, yMin, xMax, yMax+1);

		long start = ts.entryList.get(0).timestamp-ts.entryList.get(0).timestamp%(60*24);
		long end = ts.entryList.get(ts.entryList.size()-1).timestamp-ts.entryList.get(ts.entryList.size()-1).timestamp%(60*24);

		tsp.setColor(150, 150, 150);
		int start_year = TimeConverter.oleMinutesToLocalDateTime(start).getYear();
		tsp.drawText(""+start_year+"", xMin, yMax, PosHorizontal.LEFT, PosVerical.BOTTOM);


		LocalDate startDate = TimeConverter.oleMinutesToLocalDateTime(start).toLocalDate();
		
		long prev = -1;
		for(long day=start; day<=end; day++) {		
			LocalDate curr = startDate.plusDays(day-start);
			if(curr.getDayOfMonth()==1) {				
				tsp.drawLine(day-start, yMin, day-start, yMax);
				if(prev>-1) {
					tsp.drawText(TimeScale.getMonthText(curr.getMonthValue()), (prev+(day-start))/2, yMin, PosHorizontal.CENTER, PosVerical.TOP);
				}
				prev = day-start;
			}

		}


	}

	public static void drawScale(TimeSeriesPainter tsp, String sensorName) {
		setRange(tsp,sensorName);
		tsp.setColor(255, 255, 255);
		tsp.fillRect(tsp.getMinX(), tsp.getMinY(), tsp.getMaxX(), tsp.getMaxY());

		float[] r = tsp.getIndexColorRange();
		double min = r[0];
		double max = r[1];
		double range = max-min;

		double imageMin = tsp.getMinX();
		double imageMax = tsp.getMaxX();

		double scaleMin = imageMin+20;
		double scaleMax = imageMax-20;
		double scaleRange = scaleMax-scaleMin;

		ArrayList<double[]> scaleList = new ArrayList<double[]>(5);

		scaleList.add(new double[]{min,scaleMin});
		scaleList.add(new double[]{(min+max)/4,(scaleMin+scaleMax)/4});
		scaleList.add(new double[]{(min+max)/2,(scaleMin+scaleMax)/2});
		scaleList.add(new double[]{(min+max)*3/4,(scaleMin+scaleMax)*3/4});
		scaleList.add(new double[]{max, scaleMax});



		System.out.println(min+"  "+max+"  "+(min+max)+"   "+(min+max)/2);

		tsp.setColor(0, 0, 0);

		for(double[] value:scaleList) {
			tsp.drawLine((float)value[1], 0, (float)value[1], 12);
		}

		for(double[] value:scaleList) {
			tsp.drawText(""+value[0],(float) value[1], 10, PosHorizontal.CENTER, PosVerical.TOP);
		}


		for(int i=(int) scaleMin;i<=scaleMax;i++) {			
			double value = min + ((i-imageMin)*range)/scaleRange;
			tsp.setIndexedColor((float) value);
			for(int y=2;y<10;y++) {
				tsp.drawLine(i, y, i, y);
			}
		}
	}

	public static void drawRoundScale(TimeSeriesPainter tsp, String sensorName) {
		setRange(tsp,sensorName);
		tsp.setColor(255, 255, 255);
		//tsp.setColorTransparent();
		tsp.fillRect(tsp.getMinX(), tsp.getMinY(), tsp.getMaxX(), tsp.getMaxY());

		float prevX = 0;
		float prevY = 0;

		for(float d=60;d<90;d+=0.5f) {

			for(int i=0;i<720;i++) {

				tsp.setIndexedColor((float) i/2);

				float x = (float) (Math.sin((i*2*Math.PI)/720)*d)+100;
				float y = (float) (-Math.cos((i*2*Math.PI)/720)*d)+100;

				if(i==0) {
					prevX = x;
					prevY = y;
				}

				tsp.drawLine(prevX, prevY, x, y);

				prevX = x;
				prevY = y;
			}

		}

		@SuppressWarnings("unchecked")
		Pair<String, Float>[] wds = new Pair[]{
			Pair.of("N",0f),
			Pair.of("NE",45f),
			Pair.of("E",90f),
			Pair.of("SE",135f),
			Pair.of("S",180f),
			Pair.of("SW",225f),
			Pair.of("W",270f),
			Pair.of("NW",315f),
		}; 

		tsp.setColor(0, 0, 0);

		for(Pair<String, Float> wd:wds) {
			tsp.drawText(wd.a,(float) (float) (Math.sin((wd.b*2*Math.PI)/360)*75)+100, (float) (-Math.cos((wd.b*2*Math.PI)/360)*75)+100, PosHorizontal.CENTER, PosVerical.CENTER);

			tsp.drawLine(100, 100, (float) (Math.sin((wd.b*2*Math.PI)/360)*60)+100, (float) (-Math.cos((wd.b*2*Math.PI)/360)*60)+100);
		}

		tsp.fillCircle(100, 100, 30);


		/*tsp.drawText("N",(float) (float) (Math.sin((0*2*Math.PI)/360)*75)+100, (float) (-Math.cos((0*2*Math.PI)/360)*75)+100, PosHorizontal.CENTER, PosVerical.CENTER);
		tsp.drawText("NE",(float) (float) (Math.sin((45*2*Math.PI)/360)*75)+100, (float) (-Math.cos((45*2*Math.PI)/360)*75)+100, PosHorizontal.CENTER, PosVerical.CENTER);
		tsp.drawText("E",(float) (float) (Math.sin((90*2*Math.PI)/360)*75)+100, (float) (-Math.cos((90*2*Math.PI)/360)*75)+100, PosHorizontal.CENTER, PosVerical.CENTER);
		tsp.drawText("SE",(float) (float) (Math.sin((135*2*Math.PI)/360)*75)+100, (float) (-Math.cos((135*2*Math.PI)/360)*75)+100, PosHorizontal.CENTER, PosVerical.CENTER);
		tsp.drawText("S",(float) (float) (Math.sin((180*2*Math.PI)/360)*75)+100, (float) (-Math.cos((180*2*Math.PI)/360)*75)+100, PosHorizontal.CENTER, PosVerical.CENTER);
		tsp.drawText("SW",(float) (float) (Math.sin((225*2*Math.PI)/360)*75)+100, (float) (-Math.cos((225*2*Math.PI)/360)*75)+100, PosHorizontal.CENTER, PosVerical.CENTER);
		tsp.drawText("W",(float) (float) (Math.sin((270*2*Math.PI)/360)*75)+100, (float) (-Math.cos((270*2*Math.PI)/360)*75)+100, PosHorizontal.CENTER, PosVerical.CENTER);
		tsp.drawText("NW",(float) (float) (Math.sin((315*2*Math.PI)/360)*75)+100, (float) (-Math.cos((315*2*Math.PI)/360)*75)+100, PosHorizontal.CENTER, PosVerical.CENTER);*/

	}

	private static void setRange(TimeSeriesPainter tsp,String sensorName) {
		tsp.setColorScale("rainbow");
		switch(sensorName) {
		case "Ta_200":
		case "Ta_10":
		case "Ts_5":
		case "Ts_10":
		case "Ts_20":
		case "Ts_50":
		case "Tsky":
		case "Tgnd":
		case "Trad":
			//tsp.setIndexedColorRange(-10, 30);
			tsp.setIndexedColorRange(-20, 45);
			break;
		case "Albedo":
			tsp.setIndexedColorRange(0.1f, 0.3f);
			break;
		case "rH_200":
			tsp.setIndexedColorRange(0, 100);
			break;
		case "SM_10":
		case "SM_15":
		case "SM_20":
		case "SM_30":
		case "SM_40":
		case "SM_50":
			tsp.setIndexedColorRange(20, 55);
			break;
		case "B_01":
		case "B_02":
		case "B_03":
		case "B_04":
		case "B_05":
		case "B_06":
		case "B_07":
		case "B_08":
		case "B_09":
		case "B_10":
		case "B_11":
		case "B_12":
		case "B_13":
		case "B_14":
		case "B_15":
		case "B_16":
		case "B_17":
		case "B_18":
		case "B_19":
		case "B_20":
		case "B_21":
		case "B_22":
		case "B_23":
		case "B_24":
		case "B_25":
		case "B_26":
		case "B_27":
		case "B_28":
		case "B_29":
		case "B_30":
		case "Rainfall":
			tsp.setIndexedColorRange(0, 3);
			break;
		case "Fog":
			tsp.setIndexedColorRange(0, 0.3f);
			break;
		case "SWDR_300":
			tsp.setIndexedColorRange(0, 1000);
			break;
		case "SWUR_300":
			tsp.setIndexedColorRange(0, 200);
			break;
		case "LWDR_300":
			tsp.setIndexedColorRange(250, 450);
			break;
		case "LWUR_300":
			tsp.setIndexedColorRange(300, 520);
			break;
		case "PAR_200": //no data
		case "PAR_300":
			tsp.setIndexedColorRange(0, 2000);
			break;
		case "P_RT_NRT":
			tsp.setIndexedColorRange(0, 0.2f);
			break;
		case "P_container_RT":
		case "P_container_NRT":
			tsp.setIndexedColorRange(0, 600);
			break;
		case "Rn_300":
			tsp.setIndexedColorRange(-70, 700);
			break;
		case "WD":
			tsp.setIndexedColorRange(0, 360);
			tsp.setColorScale("round_rainbow");
			break;
		case "WV":
			tsp.setIndexedColorRange(0, 9);
			break;
		case "WV_gust":
			tsp.setIndexedColorRange(0, 20);
			break;						
		case "p_QNH":
			tsp.setIndexedColorRange(980, 1040);
			break;
		case "P_RT_NRT_01": //few data?
		case "P_RT_NRT_02": //few data?
		case "F_RT_NRT_01": //few data?
		case "F_RT_NRT_02": //few data?
		case "T_RT_NRT_01": //few data?
		case "T_RT_NRT_02": //few data?
			tsp.setIndexedColorRange(0, 2);
			break;
		case "swdr_01": //range?
			tsp.setIndexedColorRange(0, 1200);
			break;
		case "swdr_02": //range?
			tsp.setIndexedColorRange(0, 30);
			break;

		case "par_01": //few data?
		case "par_02": //few data?
		case "p_200": // not in schema?
		case "T_CNR": // not in schema?						
		default:
			tsp.setIndexedColorRange(-10, 30);
		}		
	}



}
