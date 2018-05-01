package org.oreon.vk.engine;

import java.nio.LongBuffer;

import org.lwjgl.vulkan.VkDevice;
import org.oreon.core.vk.core.framebuffer.FrameBufferObject;
import org.oreon.core.vk.core.pipeline.ShaderPipeline;
import org.oreon.core.vk.core.pipeline.VkPipeline;
import org.oreon.core.vk.core.pipeline.VkVertexInput;

public class OffScreenRenderPipeline extends VkPipeline{
	
	public OffScreenRenderPipeline(VkDevice device, FrameBufferObject fbo, LongBuffer layout,
			ShaderPipeline shaderPipeline, VkVertexInput vertexInput) {
		
		super(device);
		
		setVertexInput(vertexInput);
		setInputAssembly();
	    setViewportAndScissor(fbo.getWidth(), fbo.getHeight());
	    setRasterizer();
	    setMultisampling();
	    addColorBlendAttachment();
	    addColorBlendAttachment();
	    setColorBlendState();
	    setDepthAndStencilTest();
	    setDynamicState();
	    setLayout(layout);
	    createGraphicsPipeline(shaderPipeline, fbo.getRenderPass().getHandle());
	}

}