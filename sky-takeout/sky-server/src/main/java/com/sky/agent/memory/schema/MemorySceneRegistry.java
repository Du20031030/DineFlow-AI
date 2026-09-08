package com.sky.agent.memory.schema;


import java.util.List;
import java.util.Map;


public class MemorySceneRegistry {


    private static final Map<MemoryScene, List<String>>
            SCENE_NAMESPACE_MAP = Map.of(

            MemoryScene.DISH_RECOMMENDATION,

            List.of(
                    "food.taste",
                    "food.ingredient",
                    "food.category",
                    "food.budget"
            ),


            MemoryScene.ORDER_ASSIST,

            List.of(
                    "food.ingredient",
                    "food.taste"
            ),


            MemoryScene.CHAT,

            List.of()

    );


    public static List<String> getNamespaces(
            MemoryScene scene) {

        return SCENE_NAMESPACE_MAP
                .getOrDefault(
                        scene,
                        List.of()
                );
    }
}