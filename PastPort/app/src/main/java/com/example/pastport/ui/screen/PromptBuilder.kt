package com.example.pastport.ui.screen

class PromptBuilder {

    val promptTagMap = mutableMapOf(
        "restore" to false,
        "colorize" to false,
        "artistic" to false,
        "persian" to false,
        "japanese" to false,
        "european" to false
    )

    fun get(key: String): Boolean {
        return promptTagMap[key] ?: false
    }

    fun toggle(key: String) {
        val currentValue = promptTagMap[key]
        if (currentValue != null) {
            promptTagMap[key] = !currentValue
        }
    }

    fun toggleArtisticMode(key: String) {
        val artisticModes = listOf("persian", "japanese", "european")
        if (key in artisticModes) {
            for (mode in artisticModes) {
                promptTagMap[mode] = (mode == key)
            }
        }
    }

    fun build(): String {
        var promptString = ""
        if (promptTagMap["restore"] == true) {
            promptString += "Restore this old, damaged image with the goal of enhancing clarity, sharpness, and overall quality while keeping its original format intact. If the image is black and white, maintain the black and white look without adding artificial colorization. If the image is originally colored, preserve and refine the existing colors without altering their authenticity.\n" + "\n" + "Remove visible damage such as scratches, grease marks, folds, stains, cellotape marks, and torn edges. Smooth out any cracks or distortions while keeping fine details intact. Reduce noise, graininess, and pixelation, ensuring the restored image looks crisp and clear but still natural.\n" + "\n" + "Sharpen facial features, objects, and background elements to bring out clarity without making them look artificial or over-processed. Preserve the original textures and tones of clothing, skin, buildings, or natural surroundings while removing blur. Enhance contrast and brightness subtly to improve visibility while respecting the historical or natural look of the photo.\n" + "\n" + "Pay special attention to edges, outlines, and small details that may have faded due to age. The final output should feel like a well-preserved version of the original—clean, sharp, free of visible damage, and true to its original character—while maintaining its authenticity as a black and white or color image..\n"
        }
        if (promptTagMap["colorize"] == true) {
            promptString += "Colorize this black and white or grayscale image into a vivid, lifelike scene with bright, natural colors. The overall mood should feel warm, welcoming, and realistic. If the sky appears clear, render it in soft blue tones with warm golden sunlight that enhances the scene. If clouds are present, give them a natural cloudy texture, either soft white or muted gray, matching the real-world look. The weather should always feel consistent across the entire image.\n" + "\n" + "Clothing should be filled with bright, distinct colors that bring out texture, fabric details, and individuality. Add variation across people so it feels natural and authentic—no uniform coloring. Skin tones must look natural and true-to-life, reflecting the warmth of the environment.\n" + "\n" + "Buildings and structures should be enriched with earthy, realistic hues such as beige, brown, brick red, or subtle pastels, with highlights that capture sunlight or soft shade depending on their placement. Vegetation, trees, and grass should appear lush, green, and full of depth.\n" + "\n" + "Overall, the colors should feel vibrant but balanced, never oversaturated. Ensure fine details, shadows, and reflections look consistent with the chosen weather and lighting. The final result should transform the grayscale photo into a naturally colorized, bright, and immersive image.\n"
        }
        if (promptTagMap["artistic"] == true) {
            promptString += "Make the image like a old aesthetic painting.\n"
        }
        if (promptTagMap["persian"] == true) {
            promptString += "Convert this image into the style of a traditional Persian miniature painting, reflecting the rich artistic traditions of the Persian Golden Age. The final artwork should be highly detailed, intricate, and decorative, with an emphasis on fine brushwork, ornate patterns, and vibrant storytelling. Figures should appear stylized rather than realistic, with graceful postures, elongated features, and expressive hand gestures.\n" + "\n" + "Colors must be vivid and jewel-like—deep blues, emerald greens, ruby reds, gold accents, and warm ochres—applied in flat, even tones with little shading, in keeping with miniature aesthetics. Clothing and fabrics should be adorned with elaborate geometric or floral motifs, and every surface can feature ornamental detail. Backgrounds should be richly decorated, often depicting architecture, gardens, or natural scenes with symbolic rather than realistic perspective.\n" + "\n" + "The painting should incorporate calligraphic elements, such as elegant Arabic or Persian script along the borders or within cartouches, adding authenticity and narrative context. Gold leaf effects or delicate outlines may be used to highlight important elements.\n" + "\n" + "The final image should feel like an antique Persian manuscript illustration—ornamental, vibrant, and highly detailed, combining storytelling, decorative elegance, and cultural richness in the timeless tradition of Persian miniature art.\n"
        }
        if (promptTagMap["japanese"] == true) {
            promptString += "Transform this image into the style of a traditional Japanese painting from 200–300 years ago, capturing the timeless elegance of Edo-period artwork. The final result should resemble an old hand-painted scroll or screen, using delicate brushwork and natural textures. Emphasize the soft, flowing quality of sumi ink and the layered translucence of watercolor washes. Lines should be graceful, minimal, and expressive rather than rigid or overly detailed, reflecting the simplicity and harmony of classical Japanese aesthetics.\n" + "\n" + "Colors must be subtle, muted, and slightly faded, as though painted long ago—earthy tones, gentle blues, deep greens, soft reds, and warm beiges. Avoid modern brightness or saturation. Backgrounds can be simplified, with open spaces that reflect the Japanese concept of ma (negative space and balance).\n" + "\n" + "The composition should have an organic rhythm, focusing on nature, harmony, and atmosphere. If people are present, portray them in the stylized manner of traditional ukiyo-e or yamato-e, with flowing robes and graceful postures. Buildings or landscapes should feel timeless, with wooden textures, tiled roofs, mountains, rivers, or blossoming trees rendered in a poetic way.\n" + "\n" + "The final image should look like an antique Japanese painting—weathered, textured, serene, and evocative of historical artistry.\n"
        }
        if (promptTagMap["european"] == true) {
            promptString += "Convert this image into the style of a Renaissance-era European painting, inspired by the great masters such as Leonardo da Vinci, Michelangelo, and Raphael. The artwork should capture the depth, realism, and dramatic expression that defined the Renaissance period. Faces and figures should be highly detailed, with soft, realistic skin tones, expressive eyes, and lifelike anatomy that reflect humanist ideals.\n" + "\n" + "Lighting must follow the principles of chiaroscuro, with strong contrasts between light and shadow to create depth, dimension, and a dramatic atmosphere. The overall palette should be rich yet harmonious—earthy browns, deep reds, muted greens, golden highlights, and subtle blues that mimic the pigments of the 15th and 16th centuries.\n" + "\n" + "Clothing and fabrics should appear luxurious and textured, with folds, drapery, and ornamentation painted in exquisite detail. Backgrounds can include architectural elements like arches, columns, or domes, or natural landscapes rendered with perspective and atmospheric depth. If the image includes a group of people, arrange them with balance and symmetry, following the Renaissance focus on proportion and harmony.\n" + "\n" + "The final artwork should feel like a timeless Renaissance painting—realistic, dramatic, and majestic, with refined details, balanced composition, and the aura of a masterpiece from Europe’s golden age of art.\n"
        }
        if (promptString == "") return "Don't change it.\n"
        return promptString
    }
}