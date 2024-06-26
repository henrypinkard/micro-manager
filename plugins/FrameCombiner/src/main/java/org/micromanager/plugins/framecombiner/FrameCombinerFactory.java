package org.micromanager.plugins.framecombiner;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.imageprocessing.ImgSharpnessAnalysis;

public class FrameCombinerFactory implements ProcessorFactory {

   private final Studio studio_;
   private final PropertyMap settings_;

   /**
    * Factory to create the pipeline processor.
    *
    * @param studio Always present
    * @param settings Settings for the processor
    */
   public FrameCombinerFactory(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
   }

   @Override
   public Processor createProcessor() {
      String dimension = settings_.getString(FrameCombinerPlugin.PREF_PROCESSOR_DIMENSION,
              FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME);

      return new FrameCombiner(studio_,
            dimension,
            dimension.equals(FrameCombinerPlugin.PROCESSOR_DIMENSION_Z)
                    && settings_.getBoolean(FrameCombinerPlugin.PREF_USE_WHOLE_STACK, false),
            settings_.getInteger(FrameCombinerPlugin.PREF_NUMBER_OF_IMAGES_TO_PROCESS, 10),
            settings_.getString(FrameCombinerPlugin.PREF_CHANNELS_TO_AVOID, ""),
            settings_.getString(FrameCombinerPlugin.PREF_PROCESSOR_ALGO,
                      FrameCombinerPlugin.PROCESSOR_ALGO_MEAN),
            settings_.getString(FrameCombinerPlugin.PREF_SHARPNESS_ALGO,
                   ImgSharpnessAnalysis.Method.Redondo.name()),
            settings_.getBoolean(FrameCombinerPlugin.PREF_SHARPNESS_SHOW_GRAPH, false));
   }
}
