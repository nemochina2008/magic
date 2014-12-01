package tsdb.util.gui;

import static tsdb.util.AssumptionCheck.throwNull;
import static tsdb.util.AssumptionCheck.throwNulls;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tsdb.SensorCategory;
import tsdb.aggregated.AggregationInterval;
import tsdb.raw.TsEntry;
import tsdb.raw.TimestampSeries;
import tsdb.util.Util;
import tsdb.util.gui.TimeSeriesPainter.PosHorizontal;
import tsdb.util.gui.TimeSeriesPainter.PosVerical;

public class TimeSeriesDiagram {
	
	private static final Logger log = LogManager.getLogger();

	private final TimestampSeries timestampseries;
	private final AggregationInterval aggregationInterval;
	private final SensorCategory diagramType;

	private long aggregationTimeInterval;

	private float dataMinValue;
	private float dataMaxValue;
	private float dataValueRange;
	private float dataCount;
	private float dataSum;

	private long dataMinTimestamp;
	private long dataMaxTimestamp;
	private long dataTimestampRange;

	private static final float borderTop = 5;
	private static final float borderBottom = 15;
	private static final float borderLeft = 40;
	private static final float borderRight = 5;

	private float diagramMinX;
	private float diagramMinY;
	private float diagramMaxX;
	private float diagramMaxY;
	private float diagramWidth;
	private float diagramHeigh;

	private float diagramMinTimestamp;
	private float diagramMaxTimestamp;
	private float diagramTimestampRange;
	private float diagramMinValue;
	private float diagramMaxValue;
	private float diagramValueRange;
	private float diagramTimestampFactor;
	private float diagramValueFactor;

	public TimeSeriesDiagram(TimestampSeries timestampseries, AggregationInterval aggregationInterval, SensorCategory diagramType) {
		throwNulls(timestampseries,aggregationInterval);
		this.timestampseries = timestampseries;
		this.aggregationInterval = aggregationInterval;
		this.diagramType = diagramType;

		aggregationTimeInterval = 60;
		switch(aggregationInterval) {
		case HOUR:
			aggregationTimeInterval=60;
			break;
		case DAY:
			aggregationTimeInterval=1*24*60;
			break;
		case WEEK:
			aggregationTimeInterval=7*24*60;
			break;
		case MONTH:
			aggregationTimeInterval=30*24*60;//28*24*60;
			break;
		case YEAR:
			aggregationTimeInterval=365*24*60;
			break;
		default:
			System.out.println("error in agg");
		}

		dataMinValue = Float.MAX_VALUE;
		dataMaxValue = -Float.MAX_VALUE;
		dataCount = 0f;
		dataSum = 0f;

		for(TsEntry entry:timestampseries) {
			float value = entry.data[0];
			if(!Float.isNaN(value)) {
				if(value<dataMinValue) {
					dataMinValue = value;						
				}
				if(value>dataMaxValue) {
					dataMaxValue = value;						
				}
				dataCount++;
				dataSum += value;
			}
		}

		dataValueRange = dataMaxValue-dataMinValue;

		dataMinTimestamp = timestampseries.getFirstTimestamp();
		dataMaxTimestamp = timestampseries.getLastTimestamp();
		dataTimestampRange = dataMaxTimestamp-dataMinTimestamp;
		
		
		diagramMinTimestamp = dataMinTimestamp;
		diagramMaxTimestamp = dataMaxTimestamp;
		diagramTimestampRange = dataMaxTimestamp-dataMinTimestamp;
	}

	public int calcDiagramX(float timestamp) {
		return (int) (diagramMinX+((timestamp-diagramMinTimestamp)*diagramTimestampFactor));
	}
	
	public long calcTimestamp(float posX) {
		return (long) (diagramMinTimestamp+((posX - diagramMinX)/diagramTimestampFactor));
	}

	public int calcDiagramY(float value) {
		return (int) (diagramMaxY-((value-diagramMinValue)*diagramValueFactor));
	}

	private class ValueLine {
		public final float x0;
		public final float x1;
		public final float y;
		public ValueLine(float x0, float x1, float y) {
			this.x0 = x0;
			this.x1 = x1;
			this.y = y;
		}
	}

	private class ConnectLine {
		public final float x;
		public final float y0;
		public final float y1;
		public ConnectLine(float x, float y0, float y1) {
			this.x = x;
			this.y0 = y0;
			this.y1 = y1;
		}
	}

	public void draw(TimeSeriesPainter tsp) {
		draw(tsp, null);
	}

	public void draw(TimeSeriesPainter tsp, TimestampSeries compareTs) {
		throwNull(tsp);

		diagramMinX = tsp.getMinX()+borderLeft;
		diagramMinY = tsp.getMinY()+borderTop;
		diagramMaxX = tsp.getMaxX()-borderRight;
		diagramMaxY = tsp.getMaxY()-borderBottom;
		diagramWidth = diagramMaxX-diagramMinX;
		diagramHeigh = diagramMaxY-diagramMinY;

		

		diagramMinValue = dataMinValue;
		diagramMaxValue = dataMaxValue;
		diagramValueRange = dataMaxValue-dataMinValue;
		diagramTimestampFactor = diagramWidth/diagramTimestampRange;
		diagramValueFactor = diagramHeigh/diagramValueRange;

		TimeScale timescale = new TimeScale(diagramMinTimestamp,diagramMaxTimestamp);
		timescale.draw(tsp, diagramMinX, diagramMaxX, diagramMaxY+1, diagramTimestampFactor,diagramMinY,diagramMaxY+3);
		drawYScale(tsp);
		drawAxis(tsp);
		if(compareTs!=null) {
			drawGraph(tsp,compareTs,false);
		}
		drawGraph(tsp,timestampseries,true);		
	}
	
	public long getDataMinTimestamp() {
		return dataMinTimestamp;
	}
	
	public long getDataMaxTimestamp() {
		return dataMaxTimestamp;
	}
	
	public float getDiagramMinTimestamp() {
		return diagramMinTimestamp;
	}
	
	public float getDiagramMaxTimestamp() {
		return diagramMaxTimestamp;
	}
	
	public void setDiagramTimestampRange(float min, float max) {
		diagramMinTimestamp = min;
		diagramMaxTimestamp = max;
		diagramTimestampRange = max-min;
	}
	

	private void drawGraph(TimeSeriesPainter tsp, TimestampSeries ts, boolean isPrimary) {
		boolean hasPrev = false;
		float prevY = 0;
		List<ValueLine> valueLineList = new ArrayList<ValueLine>(ts.entryList.size());
		List<ConnectLine> connectLineList = new ArrayList<ConnectLine>(ts.entryList.size());

		for(TsEntry entry:ts) {
			float timestamp = entry.timestamp;
			float value = entry.data[0];
			if(Float.isNaN(value)) {
				hasPrev = false;
			} else {
				int x0 = calcDiagramX(timestamp);
				int x1 = calcDiagramX(timestamp+aggregationTimeInterval);
				int y = calcDiagramY(value);
				valueLineList.add(new ValueLine(x0, x1, y));
				if(hasPrev) {
					connectLineList.add(new ConnectLine(x0, prevY, y));
				}
				prevY = y;
				hasPrev = true;
			}

		}

		switch(diagramType) {
		case TEMPERATURE:
			drawDiagramTemperature(tsp, valueLineList, connectLineList, isPrimary);
			break;
		case WATER:
			drawDiagramWater(tsp, valueLineList, connectLineList, isPrimary);
			break;
		case OTHER:
			drawDiagramUnknown(tsp, valueLineList, connectLineList, isPrimary);
			break;
		default:
			log.error("unknown diagram type: "+diagramType);
		}
	}

	private void drawDiagramTemperature(TimeSeriesPainter tsp, List<ValueLine> valueLineList, List<ConnectLine> connectLineList, boolean isPrimary) {
		if(isPrimary) {
			tsp.setColorConnectLineTemperature();
		} else {
			tsp.setColorConnectLineTemperatureSecondary();	
		}
		for(ConnectLine connectLine:connectLineList) {
			tsp.drawLine(connectLine.x, connectLine.y0, connectLine.x, connectLine.y1);
		}

		if(isPrimary) {
			tsp.setColorValueLineTemperature();
		} else {
			tsp.setColorValueLineTemperatureSecondary();	
		}
		for(ValueLine valueLine:valueLineList) {
			tsp.drawLine(valueLine.x0,valueLine.y,valueLine.x1,valueLine.y);
		}
	}

	private void drawDiagramWater(TimeSeriesPainter tsp, List<ValueLine> valueLineList, List<ConnectLine> connectLineList, boolean isPrimary) {
		if(isPrimary) {
			tsp.setColorRectWater();
		} else {
			tsp.setColorRectWaterSecondary();	
		}
		for(ValueLine valueLine:valueLineList) {
			tsp.fillRect(valueLine.x0, valueLine.y, valueLine.x1, diagramMaxY);
		}
	}

	private void drawDiagramUnknown(TimeSeriesPainter tsp, List<ValueLine> valueLineList, List<ConnectLine> connectLineList, boolean isPrimary) {
		if(isPrimary) {
			tsp.setColorConnectLineUnknown();
		} else {
			tsp.setColorConnectLineUnknownSecondary();	
		}
		for(ConnectLine connectLine:connectLineList) {
			tsp.drawLine(connectLine.x, connectLine.y0, connectLine.x, connectLine.y1);
		}

		if(isPrimary) {
			tsp.setColorValueLineUnknown();
		} else {
			tsp.setColorValueLineUnknownSecondary();	
		}
		for(ValueLine valueLine:valueLineList) {
			tsp.drawLine(valueLine.x0,valueLine.y,valueLine.x1,valueLine.y);
		}
	}

	private void drawAxis(TimeSeriesPainter tsp) {
		tsp.setColorAxisLine();
		tsp.drawLine(diagramMinX , diagramMinY, diagramMaxX, diagramMinY); //x-Aches
		tsp.drawLine(diagramMinX , diagramMaxY, diagramMaxX, diagramMaxY); //x-Grenze
		tsp.drawLine(diagramMaxX , diagramMinY, diagramMaxX, diagramMaxY); // y-Achse
		tsp.drawLine(diagramMinX , diagramMinY, diagramMinX, diagramMaxY); // y-Grenze

		int zeroY = calcDiagramY(0f);
		if(diagramMinY<=zeroY&&zeroY<=diagramMaxY) {
			tsp.setColorZeroLine();
			tsp.drawLine(diagramMinX , zeroY, diagramMaxX, zeroY);
		}		
	}

	private void drawYScale(TimeSeriesPainter tsp) {
		int maxLines = (int) (diagramHeigh/17);
		double minLineStep = diagramValueRange/maxLines;
		double logMinLineStep = Math.pow(10d, Math.ceil(Math.log10(minLineStep)));
		double lineStep = logMinLineStep;
		if((diagramValueRange/(lineStep/5))<=maxLines) {
			lineStep /= 5d;
		} else if((diagramValueRange/(lineStep/2))<=maxLines) {
			lineStep /= 2d;
		}
		double mod = diagramMinValue%lineStep;
		float lineStart = (float) (mod>0d?diagramMinValue+lineStep-mod:diagramMinValue-mod);
		drawYScale(tsp,lineStart,lineStep);		
	}

	private void drawYScale(TimeSeriesPainter tsp, float lineStart, double lineStep) {
		float line = lineStart;
		while(line<=diagramMaxValue) {			
			int y = calcDiagramY(line);
			tsp.setColorYScaleLine();
			tsp.drawLine(diagramMinX-1 , y, diagramMaxX, y);			
			String valueText;
			if(lineStep>=1d) {
				valueText = Util.doubleToString0(line);
			} else if(lineStep>=0.1d) {
				valueText = Util.doubleToString1(line);
			} else if(lineStep>=0.01d) {
				valueText = Util.doubleToString2(line);
			} else if(lineStep>=0.001d) {
				valueText = Util.doubleToString3(line);
			} else if(lineStep>=0.0001d) {
				valueText = Util.doubleToString4(line);								
			} else {
				valueText = Util.doubleToStringFull(line);
			}			
			tsp.setColorYScaleText();
			tsp.drawText(valueText,diagramMinX-3,y,PosHorizontal.RIGHT,PosVerical.CENTER);
			line+=lineStep;
		}		
	}

}
