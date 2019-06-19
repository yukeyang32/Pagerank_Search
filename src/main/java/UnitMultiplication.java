import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.chain.ChainMapper;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UnitMultiplication {

    public static class TransitionMapper extends Mapper<Object, Text, Text, Text> {

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            // input format: fromPage\t toPage1,toPage2,toPage3
            // target: build transition matrix unit -> fromPage\t toPage=probability
            String[] fromToPages = value.toString().split("\t");
            if (fromToPages.length == 1 || fromToPages[1].trim().equals("")) {
                return;
            }
            String fromPage = fromToPages[0];
            String[] toPages = fromToPages[1].split(",");
            for (String toPage : toPages) {
                context.write(new Text(fromPage), new Text(toPage + "=" + (double) 1 / toPages.length));
            }
        }
    }

    public static class PRMapper extends Mapper<Object, Text, Text, Text> {

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            // input format: Page\t PageRank
            // target: write to reducer
            String[] pageRanks = value.toString().split("\t");
            context.write(new Text(pageRanks[0]), new Text(pageRanks[1]));
        }
    }

    public static class MultiplicationReducer extends Reducer<Text, Text, Text, Text> {

        float beta;

        @Override
        public void setup(Context context) {
            Configuration conf = context.getConfiguration();
            beta = conf.getFloat("beta", 0.2f);
        }

        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

            // input key = fromPage value=<toPage=probability..., pageRank>
            // target: get the unit multiplication
            List<String> transitionUnit = new ArrayList<String>();
            double pageRankUnit = 0;
            for (Text value : values) {
                if (value.toString().contains("=")) {
                    transitionUnit.add(value.toString());
                } else {
                    pageRankUnit = Double.parseDouble(value.toString());
                }
            }
            for (String unit : transitionUnit) {
                String toPage = unit.split("=")[0];
                double prob = Double.parseDouble(unit.split("=")[1]);
                String result = String.valueOf(prob * pageRankUnit * (1 - beta));
                context.write(new Text(toPage), new Text(result));
            }

        }

    }

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf);
        job.setJarByClass(UnitMultiplication.class);

        // how chain two mapper classes?

        job.setReducerClass(MultiplicationReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class, TransitionMapper.class);
        MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class, PRMapper.class);

        FileOutputFormat.setOutputPath(job, new Path(args[2]));
        job.waitForCompletion(true);
    }

}
