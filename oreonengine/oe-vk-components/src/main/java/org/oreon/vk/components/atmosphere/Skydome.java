package org.oreon.vk.components.atmosphere;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.vulkan.VkDevice;
import org.oreon.core.context.EngineContext;
import org.oreon.core.model.Mesh;
import org.oreon.core.model.Vertex.VertexLayout;
import org.oreon.core.scenegraph.NodeComponentType;
import org.oreon.core.scenegraph.Renderable;
import org.oreon.core.util.BufferUtil;
import org.oreon.core.util.Constants;
import org.oreon.core.util.ProceduralTexturing;
import org.oreon.core.vk.buffer.VkBuffer;
import org.oreon.core.vk.command.CommandBuffer;
import org.oreon.core.vk.context.VkContext;
import org.oreon.core.vk.descriptor.DescriptorSet;
import org.oreon.core.vk.descriptor.DescriptorSetLayout;
import org.oreon.core.vk.pipeline.ShaderModule;
import org.oreon.core.vk.pipeline.ShaderPipeline;
import org.oreon.core.vk.pipeline.VkPipeline;
import org.oreon.core.vk.pipeline.VkVertexInput;
import org.oreon.core.vk.scenegraph.VkMeshData;
import org.oreon.core.vk.scenegraph.VkRenderInfo;
import org.oreon.core.vk.util.VkAssimpModelLoader;
import org.oreon.core.vk.util.VkUtil;
import org.oreon.core.vk.wrapper.buffer.VkBufferHelper;
import org.oreon.core.vk.wrapper.buffer.VkUniformBuffer;
import org.oreon.core.vk.wrapper.command.SecondaryDrawIndexedCmdBuffer;
import org.oreon.core.vk.wrapper.pipeline.GraphicsPipeline;

public class Skydome extends Renderable{
	
	private VkPipeline graphicsPipeline;
	private VkPipeline reflectionPipeline;
	private VkUniformBuffer uniformBuffer;
	
	public Skydome() {
		
		VkDevice device = VkContext.getLogicalDevice().getHandle();
		
		getWorldTransform().setLocalScaling(Constants.ZFAR*0.5f, Constants.ZFAR*0.5f, Constants.ZFAR*0.5f);
		
		Mesh mesh = VkAssimpModelLoader.loadModel("models/obj/dome", "dome.obj").get(0).getMesh();
		ProceduralTexturing.dome(mesh);
		
		ByteBuffer ubo = memAlloc(Float.BYTES * 16);
		ubo.put(BufferUtil.createByteBuffer(getWorldTransform().getWorldMatrix()));
		ubo.flip();
		
		uniformBuffer = new VkUniformBuffer(VkContext.getLogicalDevice().getHandle(),
				VkContext.getPhysicalDevice().getMemoryProperties(),ubo);
		
		ShaderModule vertexShader = new ShaderModule(device,
				"shaders/atmosphere/atmosphere.vert.spv", VK_SHADER_STAGE_VERTEX_BIT);
		
		ShaderPipeline graphicsShaderPipeline = new ShaderPipeline(device);
	    graphicsShaderPipeline.addShaderModule(vertexShader);
	    graphicsShaderPipeline.createFragmentShader("shaders/atmosphere/atmosphere.frag.spv");
	    graphicsShaderPipeline.createShaderPipeline();
	    
	    ShaderPipeline reflectionShaderPipeline = new ShaderPipeline(device);
	    reflectionShaderPipeline.addShaderModule(vertexShader);
	    reflectionShaderPipeline.createFragmentShader("shaders/atmosphere/atmosphere_reflection.frag.spv");
	    reflectionShaderPipeline.createShaderPipeline();
	    
	    List<DescriptorSet> descriptorSets = new ArrayList<DescriptorSet>();
		List<DescriptorSetLayout> descriptorSetLayouts = new ArrayList<DescriptorSetLayout>();
		
		DescriptorSetLayout descriptorSetLayout = new DescriptorSetLayout(device,6);
	    descriptorSetLayout.addLayoutBinding(0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
	    		VK_SHADER_STAGE_VERTEX_BIT);
	    descriptorSetLayout.create();
	    DescriptorSet descriptorSet = new DescriptorSet(device,
	    		VkContext.getDescriptorPoolManager().getDescriptorPool("POOL_1").getHandle(),
	    		descriptorSetLayout.getHandlePointer());
	    descriptorSet.updateDescriptorBuffer(uniformBuffer.getHandle(),
	    		ubo.limit(), 0, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
		
		descriptorSets.add(VkContext.getCamera().getDescriptor().getSet());
		descriptorSets.add(descriptorSet);
		descriptorSetLayouts.add(VkContext.getCamera().getDescriptor().getLayout());
		descriptorSetLayouts.add(descriptorSetLayout);
		
		VkVertexInput vertexInput = new VkVertexInput(VertexLayout.POS);
		
		ByteBuffer vertexBuffer = BufferUtil.createByteBuffer(mesh.getVertices(), VertexLayout.POS);
		ByteBuffer indexBuffer = BufferUtil.createByteBuffer(mesh.getIndices());
		
		graphicsPipeline = new GraphicsPipeline(device,
				graphicsShaderPipeline, vertexInput, VkUtil.createLongBuffer(descriptorSetLayouts),
				EngineContext.getConfig().getX_ScreenResolution(),
				EngineContext.getConfig().getY_ScreenResolution(),
				VkContext.getRenderState().getOffScreenFbo().getRenderPass().getHandle(),
				VkContext.getRenderState().getOffScreenFbo().getColorAttachmentCount());
		
		reflectionPipeline = new GraphicsPipeline(device,
				reflectionShaderPipeline, vertexInput, VkUtil.createLongBuffer(descriptorSetLayouts),
				EngineContext.getConfig().getX_ScreenResolution(),
				EngineContext.getConfig().getY_ScreenResolution(),
				VkContext.getRenderState().getOffScreenReflectionFbo().getRenderPass().getHandle(),
				VkContext.getRenderState().getOffScreenReflectionFbo().getColorAttachmentCount());
		
		VkBuffer vertexBufferObject = VkBufferHelper.createDeviceLocalBuffer(
				VkContext.getLogicalDevice().getHandle(),
				VkContext.getPhysicalDevice().getMemoryProperties(),
				VkContext.getLogicalDevice().getTransferCommandPool().getHandle(),
				VkContext.getLogicalDevice().getTransferQueue(),
				vertexBuffer, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);

        VkBuffer indexBufferObject = VkBufferHelper.createDeviceLocalBuffer(
        		VkContext.getLogicalDevice().getHandle(),
        		VkContext.getPhysicalDevice().getMemoryProperties(),
        		VkContext.getLogicalDevice().getTransferCommandPool().getHandle(),
        		VkContext.getLogicalDevice().getTransferQueue(),
        		indexBuffer, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        
        CommandBuffer mainCommandBuffer = new SecondaryDrawIndexedCmdBuffer(
	    		VkContext.getLogicalDevice().getHandle(),
	    		VkContext.getLogicalDevice().getGraphicsCommandPool().getHandle(), 
	    		graphicsPipeline.getHandle(), graphicsPipeline.getLayoutHandle(),
	    		VkContext.getRenderState().getOffScreenFbo().getFrameBuffer().getHandle(),
	    		VkContext.getRenderState().getOffScreenFbo().getRenderPass().getHandle(),
	    		0,
	    		VkUtil.createLongArray(descriptorSets),
	    		vertexBufferObject.getHandle(),
	    		indexBufferObject.getHandle(),
	    		mesh.getIndices().length);
        
        CommandBuffer reflectionCommandBuffer = new SecondaryDrawIndexedCmdBuffer(
	    		VkContext.getLogicalDevice().getHandle(),
	    		VkContext.getLogicalDevice().getGraphicsCommandPool().getHandle(), 
	    		reflectionPipeline.getHandle(), reflectionPipeline.getLayoutHandle(),
	    		VkContext.getRenderState().getOffScreenReflectionFbo().getFrameBuffer().getHandle(),
	    		VkContext.getRenderState().getOffScreenReflectionFbo().getRenderPass().getHandle(),
	    		0,
	    		VkUtil.createLongArray(descriptorSets),
	    		vertexBufferObject.getHandle(),
	    		indexBufferObject.getHandle(),
	    		mesh.getIndices().length);
	    
	    VkMeshData meshData = new VkMeshData(vertexBufferObject, vertexBuffer,
	    		indexBufferObject, indexBuffer);
	    VkRenderInfo mainRenderInfo = new VkRenderInfo(mainCommandBuffer);
	    VkRenderInfo reflectionRenderInfo = new VkRenderInfo(reflectionCommandBuffer);
	    
	    addComponent(NodeComponentType.MESH_DATA, meshData);
	    addComponent(NodeComponentType.MAIN_RENDERINFO, mainRenderInfo);
	    addComponent(NodeComponentType.REFLECTION_RENDERINFO, reflectionRenderInfo);
	}
	
	public void update()
	{	
		super.update();
		
		uniformBuffer.mapMemory(BufferUtil.createByteBuffer(getWorldTransform().getWorldMatrix()));
	}

}
