# PowerShell script to populate Zookeeper with microservice configurations
Write-Host "Populating Zookeeper with microservice configurations..." -ForegroundColor Green

# Function to create Zookeeper node with configuration
function Set-ZookeeperConfig {
    param(
        [string]$Path,
        [string]$ConfigFile,
        [string]$PodName
    )
    
    Write-Host "Setting configuration for $Path..." -ForegroundColor Yellow
    
    # Read the configuration file
    $configContent = Get-Content $ConfigFile -Raw
    
    # Create the path if it doesn't exist and set the configuration
    $command = "echo 'create $Path `"$configContent`"' | /usr/bin/zookeeper-shell localhost:2181"
    kubectl exec -it $PodName -n mysillydreams-dev -- /bin/bash -c $command
    
    # If create fails (node exists), try to set the data
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Node exists, updating configuration..." -ForegroundColor Yellow
        $command = "echo 'set $Path `"$configContent`"' | /usr/bin/zookeeper-shell localhost:2181"
        kubectl exec -it $PodName -n mysillydreams-dev -- /bin/bash -c $command
    }
}

# Wait for Zookeeper to be ready
Write-Host "Waiting for Zookeeper to be ready..." -ForegroundColor Yellow
do {
    $zkPod = kubectl get pods -n mysillydreams-dev -l app=zookeeper --no-headers 2>$null
    if ($zkPod -and $zkPod.Split()[2] -eq "Running") {
        $zkPodName = $zkPod.Split()[0]
        Write-Host "Zookeeper pod found: $zkPodName" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 5
} while ($true)

# Create base paths
Write-Host "Creating base Zookeeper paths..." -ForegroundColor Yellow
kubectl exec -it $zkPodName -n mysillydreams-dev -- /bin/bash -c "echo 'create /mysillydreams \"MySillyDreams root configuration\"' | /usr/bin/zookeeper-shell localhost:2181"
kubectl exec -it $zkPodName -n mysillydreams-dev -- /bin/bash -c "echo 'create /mysillydreams/dev \"Development environment configuration\"' | /usr/bin/zookeeper-shell localhost:2181"

# Set configurations for each service
Set-ZookeeperConfig -Path "/mysillydreams/dev/auth-service" -ConfigFile "k8s/zookeeper-configs/auth-service-config.yml" -PodName $zkPodName
Set-ZookeeperConfig -Path "/mysillydreams/dev/user-service" -ConfigFile "k8s/zookeeper-configs/user-service-config.yml" -PodName $zkPodName
Set-ZookeeperConfig -Path "/mysillydreams/dev/api-gateway" -ConfigFile "k8s/zookeeper-configs/api-gateway-config.yml" -PodName $zkPodName

Write-Host "Zookeeper configuration population completed!" -ForegroundColor Green
