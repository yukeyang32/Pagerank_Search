//import org.apache.commons.math.special.Beta;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.text.DecimalFormat;

//import com.google.inject.internal.cglib.proxy.CallbackGenerator.Context;

public class UnitSum {
    public static class PassMapper extends Mapper<Object, Text, Text, DoubleWritable> {

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            // input format: toPage\t unitMultiplication
            // target: pass to reducer
            String[] pageUnit = value.toString().split("\t");
            context.write(new Text(pageUnit[0]), new DoubleWritable(Double.parseDouble(pageUnit[1])));
        }
    }

    public static class BetaMapper extends Mapper<Object,Text, Text, DoubleWritable> {
        float beta;

        @Override
        public void setup(Context context){
            Configuration conf = context.getConfiguration();
            beta = conf.getFloat("beta",0.2f);
        }

        @Override
        public void map(Object key, Text value,Context context) throws IOException, InterruptedException {
            String [] pageRank = value.toString().split("\t");
            double betaRank = beta * Double.parseDouble(pageRank[1]);
            context.write(new Text(pageRank[0]), new DoubleWritable(betaRank)); 
        }
    }



    public static class SumReducer extends Reducer<Text, DoubleWritable, Text, DoubleWritable> {


        @Override
        public void reduce(Text key, Iterable<DoubleWritable> values, Context context)
                throws IOException, InterruptedException {

            // input key = toPage value = <unitMultiplication>
            // target: sum!
            double sum = 0;
            for (DoubleWritable value : values) {
                sum += value.get();
            }
            DecimalFormat df = new DecimalFormat("#.0000");
            sum = Double.valueOf(df.format(sum));
            context.write(key, new DoubleWritable(sum));

        }
    }

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf);
        job.setJarByClass(UnitSum.class);
        job.setMapperClass(PassMapper.class);
        job.setReducerClass(SumReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(DoubleWritable.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        job.waitForCompletion(true);
    }
}
