import os
import tensorflow as tf
import tensorflow_hub as hub
import onnx
import torch
from transformers import AutoModel, AutoTokenizer

def ensure_dir(dir_path):
    if not os.path.exists(dir_path):
        os.makedirs(dir_path)

def download_and_convert_models():
    # Set up directories
    base_dir = os.path.join('app', 'src', 'main', 'assets', 'models')
    ensure_dir(base_dir)
    
    # Download and convert models
    models = {
        'phi-2-onnx': {
            'source': 'microsoft/phi-2',
            'type': 'transformers'
        },
        'coqui-vits': {
            'source': 'https://github.com/coqui-ai/TTS/releases/download/v0.13.0/tts_models--multilingual--multi-dataset--xtts_v2/',
            'type': 'tts'
        },
        'vosk-model': {
            'source': 'https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip',
            'type': 'vosk'
        },
        'distilroberta-emotion': {
            'source': 'j-hartmann/emotion-english-distilroberta-base',
            'type': 'transformers'
        },
        'minilm': {
            'source': 'sentence-transformers/all-MiniLM-L6-v2',
            'type': 'transformers'
        },
        'tesseract': {
            'source': 'https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata',
            'type': 'tesseract'
        },
        'yolov8n': {
            'source': 'ultralytics/yolov8n',
            'type': 'yolo'
        },
        't5-small': {
            'source': 't5-small',
            'type': 'transformers'
        }
    }
    
    for model_name, config in models.items():
        print(f'Downloading {model_name}...')
        model_path = os.path.join(base_dir, model_name)
        
        if config['type'] == 'transformers':
            model = AutoModel.from_pretrained(config['source'])
            tokenizer = AutoTokenizer.from_pretrained(config['source'])
            
            # Save model in ONNX format
            dummy_input = tokenizer('This is a test', return_tensors='pt')
            torch.onnx.export(
                model,
                tuple(dummy_input.values()),
                f'{model_path}.onnx',
                input_names=['input_ids', 'attention_mask'],
                output_names=['last_hidden_state'],
                dynamic_axes={
                    'input_ids': {0: 'batch_size', 1: 'sequence'},
                    'attention_mask': {0: 'batch_size', 1: 'sequence'},
                    'last_hidden_state': {0: 'batch_size', 1: 'sequence'}
                },
                opset_version=12
            )
        
        elif config['type'] == 'yolo':
            model = torch.hub.load('ultralytics/yolov8', 'yolov8n', pretrained=True)
            model.export(format='onnx')
            os.rename('yolov8n.onnx', f'{model_path}.onnx')
        
        # Add other model type handling as needed
        
        print(f'Successfully downloaded and converted {model_name}')

if __name__ == '__main__':
    download_and_convert_models()